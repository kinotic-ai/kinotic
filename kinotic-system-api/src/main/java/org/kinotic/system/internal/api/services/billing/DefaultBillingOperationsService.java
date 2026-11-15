package org.kinotic.system.internal.api.services.billing;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.BillingStatus;
import org.kinotic.system.api.services.billing.BillingOperationsService;
import org.kinotic.system.internal.api.repositories.BillingAccountRepository;
import org.kinotic.system.internal.api.repositories.BillingPlanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keeps each organization's {@link BillingAccount} as Stripe has its subscription. A webhook event only names the
 * subscription that changed: the account takes the subscription's state from Stripe as it is when the event is handled,
 * so events delivered late, twice or out of order leave the account as Stripe has it.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DefaultBillingOperationsService implements BillingOperationsService {

    // the portal's organization settings, where Checkout and the Customer Portal return to
    private static final String RETURN_PATH = "/organization-settings";
    // Stripe refuses a Checkout trial that ends sooner than this
    private static final Duration MIN_CHECKOUT_TRIAL = Duration.ofDays(2);

    private final BillingAccountRepository accountRepository;
    private final BillingPlanRepository planRepository;
    private final OrganizationService organizationService;
    private final StripeBilling stripeBilling;
    private final KinoticManagementApiProperties managementApiProperties;
    private final KinoticDomainProperties domainProperties;

    // the work under way for each organization's account, which the next change to it waits for
    private final Map<String, Future<BillingAccount>> changing = new ConcurrentHashMap<>();

    @Override
    public Future<BillingAccount> findAccount(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return accountOf(organizationId).compose(account -> trialOver(account)
                ? change(organizationId, this::endTrialIfOver)
                : Future.succeededFuture(account));
    }

    @Override
    public Future<List<BillingPlan>> findPlans(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return planRepository.findOffered(organizationId);
    }

    @Override
    public Future<String> createCheckoutSession(String organizationId, String planId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(planId, "planId cannot be blank");
        return planRepository.findById(planId).compose(plan -> {
            Validate.isTrue(plan != null && offeredTo(plan, organizationId),
                            "The plan %s is not offered to the organization %s", planId, organizationId);
            Promise<String> url = Promise.promise();
            return change(organizationId, account -> {
                Validate.validState(account.getStripeSubscriptionId() == null,
                                    "The organization already subscribes to a plan; change it in the Customer Portal");
                return ensureCustomer(account)
                        .compose(this::expireCheckout)
                        .compose(started -> stripeBilling.createCheckoutSession(organizationId,
                                                                                started.getStripeCustomerId(),
                                                                                plan,
                                                                                checkoutTrialEnd(started),
                                                                                returnUrl()))
                        .map(session -> {
                            url.complete(session.getUrl());
                            return account.setStripeCheckoutSessionId(session.getId());
                        });
            }).compose(account -> url.future());
        });
    }

    // only the Checkout Session opened last can subscribe the organization, so two Checkout pages never start two subscriptions
    private Future<BillingAccount> expireCheckout(BillingAccount account) {
        Future<BillingAccount> ret;
        if (account.getStripeCheckoutSessionId() == null) {
            ret = Future.succeededFuture(account);
        } else {
            ret = stripeBilling.expireCheckoutSession(account.getStripeCheckoutSessionId()).map(completed -> {
                Validate.validState(!completed,
                                    "The organization's subscription is being set up and shows here once Stripe confirms it");
                return account.setStripeCheckoutSessionId(null);
            });
        }
        return ret;
    }

    @Override
    public Future<String> createPortalSession(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return accountOf(organizationId).compose(account -> {
            Validate.validState(account.getStripeCustomerId() != null,
                                "The organization %s has never started a Checkout", organizationId);
            return stripeBilling.createPortalSession(account.getStripeCustomerId(), returnUrl());
        });
    }

    /**
     * Applies a webhook event to the account of the organization whose subscription it concerns. An event of a type
     * billing does not handle is ignored, as is one about a subscription that names no organization.
     */
    public Future<Void> handleEvent(Event event) {
        Future<Void> ret;
        switch (event.getType()) {
            case "checkout.session.completed" -> ret = sync(((Session) dataObject(event)).getSubscription());
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> ret = sync(((Subscription) dataObject(event)).getId());
            default -> {
                log.debug("Ignoring the Stripe event {} of type {}", event.getId(), event.getType());
                ret = Future.succeededFuture();
            }
        }
        return ret;
    }

    private Future<Void> sync(String subscriptionId) {
        Future<Void> ret;
        if (subscriptionId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = stripeBilling.findSubscription(subscriptionId).compose(subscription -> {
                String organizationId = subscription.getMetadata().get(StripeBilling.ORGANIZATION_ID_METADATA);
                Future<Void> synced;
                if (organizationId == null) {
                    log.warn("The Stripe subscription {} names no organization, so no billing account follows it", subscriptionId);
                    synced = Future.succeededFuture();
                } else {
                    // read again once the account's earlier changes are done, so a slower read of an older state
                    // cannot overwrite the state a later event already saved
                    synced = change(organizationId, account -> stripeBilling.findSubscription(subscriptionId)
                                                                            .map(current -> apply(account, current)))
                            .mapEmpty();
                }
                return synced;
            });
        }
        return ret;
    }

    // the account as the subscription leaves it; saved by change
    private BillingAccount apply(BillingAccount account, Subscription subscription) {
        Instant now = Instant.now();
        switch (subscription.getStatus()) {
            case "active", "trialing" -> account.setStatus(BillingStatus.ACTIVE)
                                                .setStripeSubscriptionId(subscription.getId())
                                                .setStripeCheckoutSessionId(null)
                                                .setPlanId(subscription.getMetadata().get(StripeBilling.PLAN_ID_METADATA))
                                                .setCurrentPeriodEnd(currentPeriodEnd(subscription))
                                                .setPastDueSince(null);
            case "past_due", "unpaid" -> account.setStatus(BillingStatus.PAST_DUE)
                                                .setStripeSubscriptionId(subscription.getId())
                                                .setStripeCheckoutSessionId(null)
                                                .setPlanId(subscription.getMetadata().get(StripeBilling.PLAN_ID_METADATA))
                                                .setCurrentPeriodEnd(currentPeriodEnd(subscription))
                                                .setPastDueSince(account.getPastDueSince() != null
                                                                         ? account.getPastDueSince()
                                                                         : Date.from(now));
            case "canceled", "incomplete_expired" -> endSubscription(account, subscription, now);
            // an incomplete subscription awaits its first payment, which settles it one way or the other
            default -> log.debug("The Stripe subscription {} of organization {} is {}; the account waits for it to settle",
                                 subscription.getId(), account.getId(), subscription.getStatus());
        }
        return account;
    }

    private void endSubscription(BillingAccount account, Subscription subscription, Instant now) {
        // an event about a subscription the account has since replaced leaves the account alone
        if (account.getStripeSubscriptionId() == null || account.getStripeSubscriptionId().equals(subscription.getId())) {
            account.setStripeSubscriptionId(null)
                   .setStripeCheckoutSessionId(null)
                   .setPlanId(null)
                   .setCurrentPeriodEnd(null);
            if (account.getTrialEndsAt().toInstant().isAfter(now)) {
                account.setStatus(BillingStatus.TRIALING).setPastDueSince(null);
            } else {
                Instant ended = subscription.getEndedAt() != null ? Instant.ofEpochSecond(subscription.getEndedAt()) : now;
                account.setStatus(BillingStatus.PAST_DUE)
                       .setPastDueSince(account.getPastDueSince() != null ? account.getPastDueSince() : Date.from(ended));
            }
        }
    }

    private Future<BillingAccount> endTrialIfOver(BillingAccount account) {
        if (trialOver(account)) {
            account.setStatus(BillingStatus.PAST_DUE).setPastDueSince(account.getTrialEndsAt());
        }
        return Future.succeededFuture(account);
    }

    private static boolean trialOver(BillingAccount account) {
        return account.getStatus() == BillingStatus.TRIALING && !account.getTrialEndsAt().after(new Date());
    }

    private Future<BillingAccount> ensureCustomer(BillingAccount account) {
        Future<BillingAccount> ret;
        if (account.getStripeCustomerId() != null) {
            ret = Future.succeededFuture(account);
        } else {
            ret = organizationService.findById(account.getId())
                                     .compose(organization -> stripeBilling.createCustomer(account.getId(), organization.getName()))
                                     .map(account::setStripeCustomerId);
        }
        return ret;
    }

    // runs change on the organization's account, created with a free trial if it has none, and saves what it leaves;
    // the changes to one account run one after another on this node, each reading what the one before it saved
    private Future<BillingAccount> change(String organizationId, Function<BillingAccount, Future<BillingAccount>> change) {
        Future<BillingAccount> ret = changing.compute(organizationId, (id, previous) -> {
            Future<Void> before = previous != null ? previous.transform(ar -> Future.<Void>succeededFuture()) : Future.succeededFuture();
            return before.compose(v -> accountOf(organizationId))
                         .compose(change)
                         .compose(account -> accountRepository.save(account.setUpdated(new Date())));
        });
        ret.onComplete(ar -> changing.remove(organizationId, ret));
        return ret;
    }

    private Future<BillingAccount> accountOf(String organizationId) {
        return accountRepository.findById(organizationId).compose(account -> {
            Future<BillingAccount> ret;
            if (account != null) {
                ret = Future.succeededFuture(account);
            } else {
                ret = organizationService.findById(organizationId).compose(organization -> {
                    Validate.isTrue(organization != null, "No organization has the id %s", organizationId);
                    Date created = organization.getCreated() != null ? organization.getCreated() : new Date();
                    Duration trial = Duration.ofDays(managementApiProperties.getManagementApi().getBilling().getTrialDays());
                    Date now = new Date();
                    BillingAccount started = new BillingAccount().setId(organizationId)
                                                                 .setStatus(BillingStatus.TRIALING)
                                                                 .setTrialEndsAt(Date.from(created.toInstant().plus(trial)))
                                                                 .setCreated(now)
                                                                 .setUpdated(now);
                    // another node may create the account at the same time, and its account is the one kept
                    return accountRepository.create(started)
                                            .recover(error -> error instanceof AlreadyExistsException
                                                    ? accountRepository.findById(organizationId)
                                                    : Future.failedFuture(error));
                });
            }
            return ret;
        });
    }

    // the rule BillingPlanRepository.findOffered queries by
    private static boolean offeredTo(BillingPlan plan, String organizationId) {
        return !plan.isArchived() && (plan.getOrganizationId() == null || plan.getOrganizationId().equals(organizationId));
    }

    // a subscription started with time left in the trial charges first when the trial ends
    private static Instant checkoutTrialEnd(BillingAccount account) {
        Instant trialEnd = account.getTrialEndsAt().toInstant();
        return trialEnd.isAfter(Instant.now().plus(MIN_CHECKOUT_TRIAL)) ? trialEnd : null;
    }

    private String returnUrl() {
        return domainProperties.getDomain().getPortalBaseUrl() + RETURN_PATH;
    }

    private static Date currentPeriodEnd(Subscription subscription) {
        Date ret = null;
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            SubscriptionItem item = subscription.getItems().getData().getFirst();
            ret = item.getCurrentPeriodEnd() != null ? Date.from(Instant.ofEpochSecond(item.getCurrentPeriodEnd())) : null;
        }
        return ret;
    }

    // an event whose API version differs from the library's deserializes through the unsafe path, which maps what it can
    private static StripeObject dataObject(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("The Stripe event " + event.getId() + " could not be read", e);
            }
        });
    }
}
