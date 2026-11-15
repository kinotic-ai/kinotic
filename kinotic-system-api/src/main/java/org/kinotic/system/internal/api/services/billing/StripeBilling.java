package org.kinotic.system.internal.api.services.billing;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.billing.Meter;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductUpdateParams;
import com.stripe.param.billing.MeterCreateParams;
import com.stripe.param.billing.MeterListParams;
import com.stripe.param.checkout.SessionCreateParams;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.model.billing.BillingInterval;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.PlanComponent;
import org.kinotic.management.api.model.billing.PlanComponentType;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every call the system makes to Stripe for billing organizations. Stripe objects carry the ids of the Kinotic records
 * they belong to in their metadata, under {@link #ORGANIZATION_ID_METADATA} and {@link #PLAN_ID_METADATA}.
 */
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StripeBilling {

    /** The metadata key of the organization a Stripe Customer or Subscription belongs to. */
    public static final String ORGANIZATION_ID_METADATA = "kinotic_organization_id";
    /** The metadata key of the plan a Stripe Product, Price or Subscription belongs to. */
    public static final String PLAN_ID_METADATA = "kinotic_plan_id";

    // a Checkout Session's status values
    private static final String CHECKOUT_OPEN = "open";
    private static final String CHECKOUT_COMPLETE = "complete";
    private static final Set<String> ENDED_SUBSCRIPTION_STATUSES = Set.of("canceled", "incomplete_expired");

    // a meter event names the Stripe Customer, and carries the usage, under these payload keys
    private static final String CUSTOMER_PAYLOAD_KEY = "stripe_customer_id";
    private static final String VALUE_PAYLOAD_KEY = "value";
    private static final int METERS_PAGE_SIZE = 100;

    private final StripeClient stripe;
    private final KinoticManagementApiProperties properties;
    private final Vertx vertx;

    // the Stripe Billing Meter of each usage meter, found or created the first time a plan meters it
    private final Map<UsageMeter, String> meterIds = new ConcurrentHashMap<>();

    /**
     * Creates the plan's Stripe Product, and a Stripe Price for each component, which it sets on the component.
     *
     * @param plan a valid plan, with its id
     * @return the plan, with the ids of its Stripe objects
     */
    public Future<BillingPlan> createProduct(BillingPlan plan) {
        return blocking(() -> {
            ProductCreateParams.Builder product = ProductCreateParams.builder()
                                                                     .setName(plan.getName())
                                                                     .putMetadata(PLAN_ID_METADATA, plan.getId());
            if (plan.getDescription() != null && !plan.getDescription().isBlank()) {
                product.setDescription(plan.getDescription());
            }
            Product created = stripe.v1().products().create(product.build());
            plan.setStripeProductId(created.getId());
            for (PlanComponent component : plan.getComponents()) {
                component.setStripePriceId(createPrice(plan, component).getId());
            }
            return plan;
        });
    }

    /** Marks the Stripe Product inactive, so no new subscription can start on its Prices. */
    public Future<Void> archiveProduct(String productId) {
        return blocking(() -> {
            stripe.v1().products().update(productId, ProductUpdateParams.builder().setActive(false).build());
            return null;
        });
    }

    /**
     * Creates the Stripe Customer of an organization.
     *
     * @return the Customer's id
     */
    public Future<String> createCustomer(String organizationId, String organizationName) {
        return blocking(() -> stripe.v1().customers().create(CustomerCreateParams.builder()
                                                                                 .setName(organizationName)
                                                                                 .putMetadata(ORGANIZATION_ID_METADATA, organizationId)
                                                                                 .build()).getId());
    }

    /**
     * Opens a Checkout Session that subscribes the Customer to the plan, with the organization and plan in the
     * Subscription's metadata.
     *
     * @param trialEnd when the Subscription's first charge is, or {@code null} to charge at once
     * @return the Checkout Session, whose URL is the Checkout page
     */
    public Future<Session> createCheckoutSession(String organizationId,
                                                String customerId,
                                                BillingPlan plan,
                                                Instant trialEnd,
                                                String returnUrl) {
        return blocking(() -> {
            SessionCreateParams.SubscriptionData.Builder subscription =
                    SessionCreateParams.SubscriptionData.builder()
                                                        .putMetadata(ORGANIZATION_ID_METADATA, organizationId)
                                                        .putMetadata(PLAN_ID_METADATA, plan.getId());
            if (trialEnd != null) {
                subscription.setTrialEnd(trialEnd.getEpochSecond());
            }
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                                                                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                                                                    .setCustomer(customerId)
                                                                    .setClientReferenceId(organizationId)
                                                                    .setSuccessUrl(returnUrl)
                                                                    .setCancelUrl(returnUrl)
                                                                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                                                                    .setSubscriptionData(subscription.build());
            for (PlanComponent component : plan.getComponents()) {
                SessionCreateParams.LineItem.Builder item = SessionCreateParams.LineItem.builder()
                                                                                        .setPrice(component.getStripePriceId());
                // a metered Price is billed on the usage reported, so its line item takes no quantity
                if (component.getType() == PlanComponentType.FLAT) {
                    item.setQuantity(1L);
                }
                params.addLineItem(item.build());
            }
            return stripe.v1().checkout().sessions().create(params.build());
        });
    }

    /**
     * Makes the Checkout Session unable to subscribe anyone: an open Session is expired, and one already expired is
     * left as it is.
     *
     * @return {@code true} when the Session was already completed and its Subscription is being created or has not
     *         ended
     */
    public Future<Boolean> expireCheckoutSession(String sessionId) {
        return blocking(() -> {
            Session session = stripe.v1().checkout().sessions().retrieve(sessionId);
            if (CHECKOUT_OPEN.equals(session.getStatus())) {
                session = stripe.v1().checkout().sessions().expire(sessionId);
            }
            boolean ret = false;
            if (CHECKOUT_COMPLETE.equals(session.getStatus())) {
                ret = session.getSubscription() == null
                        || !ENDED_SUBSCRIPTION_STATUSES.contains(stripe.v1().subscriptions().retrieve(session.getSubscription()).getStatus());
            }
            return ret;
        });
    }

    /**
     * Opens a Customer Portal session for the Customer.
     *
     * @return the Customer Portal page's URL
     */
    public Future<String> createPortalSession(String customerId, String returnUrl) {
        return blocking(() -> stripe.v1().billingPortal().sessions().create(
                com.stripe.param.billingportal.SessionCreateParams.builder()
                                                                   .setCustomer(customerId)
                                                                   .setReturnUrl(returnUrl)
                                                                   .build()).getUrl());
    }

    /** The Subscription {@code subscriptionId} as Stripe has it now. */
    public Future<Subscription> findSubscription(String subscriptionId) {
        return blocking(() -> stripe.v1().subscriptions().retrieve(subscriptionId));
    }

    /**
     * Reads a webhook delivery.
     *
     * @return the event the delivery carries
     * @throws SignatureVerificationException when the delivery is not signed with the endpoint's signing secret
     */
    public Event verifyEvent(String payload, String signature) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signature,
                                      properties.getManagementApi().getBilling().getStripeWebhookSecret());
    }

    private Price createPrice(BillingPlan plan, PlanComponent component) throws Exception {
        PriceCreateParams.Recurring.Builder recurring = PriceCreateParams.Recurring.builder()
                                                                                   .setInterval(intervalOf(plan.getInterval()));
        PriceCreateParams.Builder price = PriceCreateParams.builder()
                                                           .setProduct(plan.getStripeProductId())
                                                           .setCurrency(plan.getCurrency())
                                                           .putMetadata(PLAN_ID_METADATA, plan.getId());
        if (component.getType() == PlanComponentType.FLAT) {
            price.setUnitAmount(component.getAmount());
        } else {
            recurring.setUsageType(PriceCreateParams.Recurring.UsageType.METERED)
                     .setMeter(meterIdOf(component.getMeter()));
            price.setNickname(component.getMeter().getDisplayName());
            if (component.getIncludedQuantity() > 0) {
                // the included units are a first tier at no charge
                price.setBillingScheme(PriceCreateParams.BillingScheme.TIERED)
                     .setTiersMode(PriceCreateParams.TiersMode.GRADUATED)
                     .addTier(PriceCreateParams.Tier.builder()
                                                    .setUpTo(component.getIncludedQuantity())
                                                    .setUnitAmount(0L)
                                                    .build())
                     .addTier(PriceCreateParams.Tier.builder()
                                                    .setUpTo(PriceCreateParams.Tier.UpTo.INF)
                                                    .setUnitAmountDecimal(component.getUnitAmount())
                                                    .build());
            } else {
                price.setUnitAmountDecimal(component.getUnitAmount());
            }
        }
        return stripe.v1().prices().create(price.setRecurring(recurring.build()).build());
    }

    // runs on a worker thread, as every caller of it does
    private String meterIdOf(UsageMeter meter) throws Exception {
        String ret = meterIds.get(meter);
        if (ret == null) {
            Meter found = findMeter(meter);
            if (found == null) {
                try {
                    found = createMeter(meter);
                } catch (InvalidRequestException e) {
                    // a meter's event name is unique: another request or node that created it first leaves it to reuse
                    found = findMeter(meter);
                    if (found == null) {
                        throw e;
                    }
                }
            }
            ret = found.getId();
            meterIds.put(meter, ret);
        }
        return ret;
    }

    private Meter findMeter(UsageMeter meter) throws StripeException {
        Meter ret = null;
        for (Meter existing : stripe.v1().billing().meters().list(MeterListParams.builder()
                                                                                 .setStatus(MeterListParams.Status.ACTIVE)
                                                                                 .setLimit((long) METERS_PAGE_SIZE)
                                                                                 .build()).autoPagingIterable()) {
            if (meter.getEventName().equals(existing.getEventName())) {
                ret = existing;
                break;
            }
        }
        return ret;
    }

    private Meter createMeter(UsageMeter meter) throws StripeException {
        return stripe.v1().billing().meters().create(
                MeterCreateParams.builder()
                                 .setDisplayName(meter.getDisplayName())
                                 .setEventName(meter.getEventName())
                                 .setDefaultAggregation(MeterCreateParams.DefaultAggregation.builder()
                                                                                           .setFormula(MeterCreateParams.DefaultAggregation.Formula.SUM)
                                                                                           .build())
                                 .setCustomerMapping(MeterCreateParams.CustomerMapping.builder()
                                                                                      .setType(MeterCreateParams.CustomerMapping.Type.BY_ID)
                                                                                      .setEventPayloadKey(CUSTOMER_PAYLOAD_KEY)
                                                                                      .build())
                                 .setValueSettings(MeterCreateParams.ValueSettings.builder()
                                                                                  .setEventPayloadKey(VALUE_PAYLOAD_KEY)
                                                                                  .build())
                                 .build());
    }

    private static PriceCreateParams.Recurring.Interval intervalOf(BillingInterval interval) {
        return switch (interval) {
            case MONTH -> PriceCreateParams.Recurring.Interval.MONTH;
            case YEAR -> PriceCreateParams.Recurring.Interval.YEAR;
        };
    }

    // stripe-java blocks on its HTTP calls, so every call runs on a worker thread
    private <T> Future<T> blocking(Callable<T> call) {
        return vertx.executeBlocking(call, false);
    }
}
