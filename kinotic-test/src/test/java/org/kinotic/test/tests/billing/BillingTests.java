package org.kinotic.test.tests.billing;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingInterval;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.BillingStatus;
import org.kinotic.management.api.model.billing.PlanComponent;
import org.kinotic.management.api.model.billing.PlanComponentType;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.services.billing.BillingService;
import org.kinotic.system.api.services.billing.BillingOperationsService;
import org.kinotic.system.api.services.billing.BillingPlanService;
import org.kinotic.system.internal.api.services.billing.DefaultBillingOperationsService;
import org.kinotic.test.support.billing.StripeRequest;
import org.kinotic.test.support.billing.StripeStandIn;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Billing organizations through Stripe, against a stand-in of the Stripe API: the plans system users create, the free
 * trial every organization starts on, Checkout, and the account following the subscription's webhook events.
 */
@SpringBootTest
public class BillingTests extends KinoticTestBase {

    private static final int TRIAL_DAYS = 30;

    @Autowired
    private BillingPlanService plans;
    @Autowired
    private BillingOperationsService operations;
    @Autowired
    private DefaultBillingOperationsService webhookHandler;
    @Autowired
    private BillingService billingService;
    @Autowired
    private OrganizationService organizations;

    @Test
    public void aPlanCreatesItsProductAndAPricePerComponent() throws Exception {
        BillingPlan plan = await(plans.create(teamPlan(null)));

        assertNotNull(plan.getId());
        assertNotNull(plan.getStripeProductId());
        plan.getComponents().forEach(component -> assertNotNull(component.getStripePriceId()));
        List<StripeRequest> prices = StripeStandIn.requests("POST", "/v1/prices").stream()
                                                  .filter(request -> plan.getId().equals(request.params().get("metadata[kinotic_plan_id]")))
                                                  .toList();
        assertEquals(2, prices.size());
        Map<String, String> flat = prices.get(0).params();
        assertEquals("4900", flat.get("unit_amount"));
        assertEquals("month", flat.get("recurring[interval]"));
        Map<String, String> metered = prices.get(1).params();
        assertEquals("metered", metered.get("recurring[usage_type]"));
        assertNotNull(metered.get("recurring[meter]"));
        assertEquals("graduated", metered.get("tiers_mode"));
        assertEquals("100", metered.get("tiers[0][up_to]"));
        assertEquals("0", metered.get("tiers[0][unit_amount]"));
        assertEquals("inf", metered.get("tiers[1][up_to]"));
        assertEquals("2.5", metered.get("tiers[1][unit_amount_decimal]"));
    }

    @Test
    public void aPlanThatMetersTheSameMeterTwiceIsRefused() {
        BillingPlan plan = teamPlan(null);
        plan.getComponents().add(new PlanComponent().setType(PlanComponentType.METERED)
                                                    .setMeter(UsageMeter.VCPU_HOURS)
                                                    .setUnitAmount(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> await(plans.create(plan)));
    }

    @Test
    public void aNewOrganizationStartsOnAFreeTrial() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("billing-" + suffix())));

        BillingAccount account = await(operations.findAccount(organization.getId()));

        assertEquals(BillingStatus.TRIALING, account.getStatus());
        assertEquals(organization.getCreated().toInstant().plus(Duration.ofDays(TRIAL_DAYS)), account.getTrialEndsAt().toInstant());
    }

    @Test
    public void aTrialThatEndedWithNoSubscriptionLeavesTheAccountPastDue() throws Exception {
        Date created = Date.from(Instant.now().minus(Duration.ofDays(TRIAL_DAYS + 3)));
        Organization organization = await(organizations.save(new Organization().setId("billing-" + suffix())
                                                                                  .setName("Billing " + suffix())
                                                                                  .setCreated(created)));

        BillingAccount account = await(operations.findAccount(organization.getId()));

        assertEquals(BillingStatus.PAST_DUE, account.getStatus());
        assertEquals(account.getTrialEndsAt(), account.getPastDueSince());
    }

    @Test
    public void aMemberSubscribesThroughCheckoutChargedWhenTheTrialEnds() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("billing-" + suffix())));
        BillingPlan plan = await(plans.create(teamPlan(null)));

        String url = await(runAs(memberOf(organization), () -> billingService.createCheckoutSession(plan.getId())));

        assertTrue(url.startsWith("https://checkout.stripe.test/"));
        StripeRequest checkout = StripeStandIn.requests("POST", "/v1/checkout/sessions").stream()
                                              .filter(request -> organization.getId().equals(request.params().get("client_reference_id")))
                                              .findFirst().orElseThrow();
        BillingAccount account = await(operations.findAccount(organization.getId()));
        assertEquals(account.getStripeCustomerId(), checkout.params().get("customer"));
        assertEquals(String.valueOf(account.getTrialEndsAt().toInstant().getEpochSecond()),
                     checkout.params().get("subscription_data[trial_end]"));
        assertEquals(organization.getId(), checkout.params().get("subscription_data[metadata][kinotic_organization_id]"));
        assertEquals(plan.getComponents().get(0).getStripePriceId(), checkout.params().get("line_items[0][price]"));
        assertEquals("1", checkout.params().get("line_items[0][quantity]"));
        assertNull(checkout.params().get("line_items[1][quantity]"));
    }

    @Test
    public void openingCheckoutAgainExpiresTheEarlierPage() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("billing-" + suffix())));
        BillingPlan plan = await(plans.create(teamPlan(null)));

        String first = await(runAs(memberOf(organization), () -> billingService.createCheckoutSession(plan.getId())));
        String second = await(runAs(memberOf(organization), () -> billingService.createCheckoutSession(plan.getId())));

        String firstId = first.substring(first.lastIndexOf('/') + 1);
        String secondId = second.substring(second.lastIndexOf('/') + 1);
        assertEquals(1, StripeStandIn.requests("POST", "/v1/checkout/sessions/" + firstId + "/expire").size());
        assertEquals(secondId, await(operations.findAccount(organization.getId())).getStripeCheckoutSessionId());
    }

    @Test
    public void aPlanOfferedToAnotherOrganizationCannotBeBought() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("billing-" + suffix())));
        Organization other = await(organizations.create(new Organization().setName("billing-" + suffix())));
        BillingPlan plan = await(plans.create(teamPlan(other.getId())));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                                                 () -> await(operations.createCheckoutSession(organization.getId(), plan.getId())));
        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertTrue(await(operations.findPlans(other.getId())).stream().anyMatch(offered -> offered.getId().equals(plan.getId())));
    }

    @Test
    public void theAccountFollowsItsSubscription() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("billing-" + suffix())));
        BillingPlan plan = await(plans.create(teamPlan(null)));
        String subscriptionId = "sub_" + suffix();
        long periodEnd = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();

        await(webhookHandler.handleEvent(subscriptionEvent(subscription(subscriptionId, organization, plan, "active", periodEnd))));
        BillingAccount active = await(operations.findAccount(organization.getId()));
        assertEquals(BillingStatus.ACTIVE, active.getStatus());
        assertEquals(plan.getId(), active.getPlanId());
        assertEquals(subscriptionId, active.getStripeSubscriptionId());
        assertEquals(periodEnd, active.getCurrentPeriodEnd().toInstant().getEpochSecond());

        await(webhookHandler.handleEvent(subscriptionEvent(subscription(subscriptionId, organization, plan, "past_due", periodEnd))));
        BillingAccount pastDue = await(operations.findAccount(organization.getId()));
        assertEquals(BillingStatus.PAST_DUE, pastDue.getStatus());
        assertNotNull(pastDue.getPastDueSince());

        // cancelled while the trial still runs, the organization is back on its trial
        await(webhookHandler.handleEvent(subscriptionEvent(subscription(subscriptionId, organization, plan, "canceled", periodEnd))));
        BillingAccount cancelled = await(operations.findAccount(organization.getId()));
        assertEquals(BillingStatus.TRIALING, cancelled.getStatus());
        assertNull(cancelled.getStripeSubscriptionId());
        assertNull(cancelled.getPastDueSince());
    }

    private static BillingPlan teamPlan(String organizationId) {
        return new BillingPlan().setName("Team " + suffix())
                                .setOrganizationId(organizationId)
                                .setCurrency("usd")
                                .setInterval(BillingInterval.MONTH)
                                .setComponents(new ArrayList<>(List.of(
                                        new PlanComponent().setType(PlanComponentType.FLAT).setAmount(4900L),
                                        new PlanComponent().setType(PlanComponentType.METERED)
                                                           .setMeter(UsageMeter.VCPU_HOURS)
                                                           .setUnitAmount(new BigDecimal("2.5"))
                                                           .setIncludedQuantity(100))));
    }

    private static DefaultOrganizationParticipant memberOf(Organization organization) {
        return new DefaultOrganizationParticipant("billing-member-" + suffix(),
                                                  organization.getId(),
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                         ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                  List.of("ADMIN"));
    }

    // the subscription as Stripe has it from now on
    private static JsonObject subscription(String id, Organization organization, BillingPlan plan, String status, long periodEnd) {
        return StripeStandIn.report(new JsonObject().put("id", id)
                                                    .put("object", "subscription")
                                                    .put("status", status)
                                                    .put("metadata", new JsonObject().put("kinotic_organization_id", organization.getId())
                                                                                     .put("kinotic_plan_id", plan.getId()))
                                                    .put("items", new JsonObject().put("object", "list")
                                                                                  .put("data", new JsonArray().add(
                                                                                          new JsonObject().put("id", "si_" + suffix())
                                                                                                          .put("object", "subscription_item")
                                                                                                          .put("current_period_end", periodEnd)))));
    }

    private static Event subscriptionEvent(JsonObject subscription) {
        JsonObject event = new JsonObject().put("id", "evt_" + suffix())
                                           .put("object", "event")
                                           .put("type", "customer.subscription.updated")
                                           .put("api_version", Stripe.API_VERSION)
                                           .put("data", new JsonObject().put("object", subscription));
        return Webhook.constructEventWithoutVerification(event.encode());
    }
}
