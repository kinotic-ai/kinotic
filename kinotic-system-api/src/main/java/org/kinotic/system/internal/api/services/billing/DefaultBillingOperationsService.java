package org.kinotic.system.internal.api.services.billing;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingForecast;
import org.kinotic.management.api.model.billing.BillingInvoice;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.BillingStatus;
import org.kinotic.management.api.model.billing.InvoiceStatus;
import org.kinotic.management.api.model.billing.PlanComponent;
import org.kinotic.management.api.model.billing.PlanComponentType;
import org.kinotic.management.api.model.billing.SubscriptionPeriod;
import org.kinotic.management.api.model.billing.UsageLine;
import org.kinotic.management.api.model.billing.UsageMeasurementType;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.model.billing.UsageRecord;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.repositories.AbstractDeploymentRepository;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.services.billing.BillingOperationsService;
import org.kinotic.system.internal.api.model.BillableResource;
import org.kinotic.system.internal.api.repositories.BillableResourceRepository;
import org.kinotic.system.internal.api.repositories.BillingAccountRepository;
import org.kinotic.system.internal.api.repositories.BillingPlanRepository;
import org.kinotic.system.internal.api.repositories.UsageRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

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

    // the Billing tab of the portal's organization settings, where Checkout and the Customer Portal return to
    private static final String RETURN_PATH = "/organization-settings?tab=billing";
    // Stripe refuses a Checkout trial that ends sooner than this
    private static final Duration MIN_CHECKOUT_TRIAL = Duration.ofDays(2);
    private static final Duration MAX_USAGE_RANGE = Duration.ofDays(93);
    private static final int MAX_INVOICES = 24;
    // how long payment may be due before the organization is deactivated
    private static final Duration GRACE_PERIOD = Duration.ofDays(14);
    // how long an ended subscription's period is kept; longer than BillingScheduler.CATCH_UP, so every hour it covers settles first
    private static final Duration ENDED_PERIOD_RETENTION = Duration.ofDays(7);
    private static final int PAGE_SIZE = 200;

    private final BillingAccountRepository accountRepository;
    private final BillingPlanRepository planRepository;
    private final UsageRecordRepository usageRepository;
    private final BillableResourceRepository resourceRepository;
    private final OrganizationService organizationService;
    private final StripeBilling stripeBilling;
    private final KinoticManagementApiProperties managementApiProperties;
    private final KinoticDomainProperties domainProperties;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final Ignite ignite;

    // Every node requests the deployment; Ignite elects a single host for it
    @EventListener(ApplicationReadyEvent.class)
    public void deployScheduler() {
        BillingScheduler.deploy(ignite);
    }

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
            // the URL of each Session an attempt opened, by Session id; only the Session of the attempt saved is handed out
            Map<String, String> opened = new ConcurrentHashMap<>();
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
                            opened.put(session.getId(), session.getUrl());
                            return account.setStripeCheckoutSessionId(session.getId());
                        });
            }).transform(saved -> {
                String savedSessionId = saved.succeeded() ? saved.result().getStripeCheckoutSessionId() : null;
                return expireUnsaved(opened.keySet(), savedSessionId)
                        .compose(v -> saved.succeeded()
                                ? Future.succeededFuture(opened.get(savedSessionId))
                                : Future.failedFuture(saved.cause()));
            });
        });
    }

    // a Session opened by an attempt that lost to another write was never handed out, and is expired so it cannot subscribe
    private Future<Void> expireUnsaved(Set<String> sessionIds, String savedSessionId) {
        List<Future<Boolean>> expiring = sessionIds.stream()
                .filter(sessionId -> !sessionId.equals(savedSessionId))
                .map(sessionId -> stripeBilling.expireCheckoutSession(sessionId).recover(error -> {
                    log.warn("Could not expire the unsaved Checkout Session {}", sessionId, error);
                    return Future.succeededFuture(false);
                }))
                .toList();
        return Future.join(expiring).mapEmpty();
    }

    // only the Checkout Session opened last can subscribe the organization, so two Checkout pages never start two subscriptions
    private Future<BillingAccount> expireCheckout(BillingAccount account) {
        Future<BillingAccount> ret;
        if (account.getStripeCheckoutSessionId() == null) {
            ret = Future.succeededFuture(account);
        } else {
            ret = stripeBilling.expireCheckoutSession(account.getStripeCheckoutSessionId()).map(subscribing -> {
                Validate.validState(!subscribing,
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

    @Override
    public Future<List<UsageRecord>> findUsage(String organizationId, Date from, Date to) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notNull(from, "from cannot be null");
        Validate.notNull(to, "to cannot be null");
        Validate.isTrue(from.before(to), "from must be before to");
        Validate.isTrue(!to.toInstant().isAfter(from.toInstant().plus(MAX_USAGE_RANGE)),
                        "The range cannot be longer than %d days", MAX_USAGE_RANGE.toDays());
        return usageRepository.findForOrganization(organizationId, from, to);
    }

    @Override
    public Future<BillingForecast> findForecast(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findAccount(organizationId).compose(account -> {
            Instant now = Instant.now();
            Future<BillingForecast> ret;
            if (account.getStripeSubscriptionId() == null
                    || account.getPlanId() == null
                    || account.getCurrentPeriodStart() == null
                    || account.getCurrentPeriodEnd() == null
                    || account.getTrialEndsAt().toInstant().isAfter(now)) {
                ret = Future.succeededFuture();
            } else {
                ret = planRepository.findById(account.getPlanId()).compose(plan -> {
                    Validate.validState(plan != null, "The plan %s the organization subscribes to does not exist", account.getPlanId());
                    Instant periodStart = account.getCurrentPeriodStart().toInstant();
                    Instant periodEnd = account.getCurrentPeriodEnd().toInstant();
                    // usage before the trial ended is not billed, so the period's billed usage starts when it ended
                    Instant measuredFrom = latest(periodStart, account.getTrialEndsAt().toInstant());
                    return Future.all(CursorPaging.readAll(pageable -> usageRepository.findPageForOrganization(organizationId,
                                                                                                               Date.from(measuredFrom),
                                                                                                               Date.from(now),
                                                                                                               pageable),
                                                           PAGE_SIZE,
                                                           Sort.by("id")),
                                      resourceRepository.findOpenForOrganization(organizationId))
                                 .map(found -> forecast(plan, periodStart, periodEnd, measuredFrom, found.resultAt(0), found.resultAt(1)));
                });
            }
            return ret;
        });
    }

    @Override
    public Future<List<BillingInvoice>> findInvoices(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return accountOf(organizationId).compose(account -> account.getStripeCustomerId() == null
                ? Future.succeededFuture(List.of())
                : stripeBilling.findInvoices(account.getStripeCustomerId(), MAX_INVOICES)
                               .map(invoices -> invoices.stream().map(DefaultBillingOperationsService::invoice).toList()));
    }

    private static BillingForecast forecast(BillingPlan plan,
                                            Instant periodStart,
                                            Instant periodEnd,
                                            Instant measuredFrom,
                                            List<UsageRecord> records,
                                            List<BillableResource> running) {
        BillingForecast ret = new BillingForecast().setPlanId(plan.getId())
                                                   .setCurrency(plan.getCurrency())
                                                   .setPeriodStart(Date.from(periodStart))
                                                   .setPeriodEnd(Date.from(periodEnd));
        for (UsageRecord record : records) {
            record.getLines().forEach(line -> ret.getUsageToDate().merge(line.getMeter(), line.getQuantity(), Double::sum));
        }
        ret.getProjectedUsage().putAll(ret.getUsageToDate());
        // the runs going on now go on to the period's end from the last hour settled, which holds their usage up to then
        Instant settledThrough = measuredFrom;
        for (UsageRecord record : records) {
            Instant end = record.getHour().toInstant().plus(Duration.ofHours(1));
            if (record.getSettled() != null && end.isAfter(settledThrough)) {
                settledThrough = end;
            }
        }
        for (BillableResource resource : running) {
            double runningHours = hoursBetween(latest(settledThrough, resource.getStarted().toInstant()), periodEnd);
            resource.getAmounts().forEach(amount -> ret.getProjectedUsage().merge(amount.getMeter(), amount.getPerHour() * runningHours, Double::sum));
        }
        // each polled level stays at its last reading to the period's end, from the end of the hour of that reading
        Map<UsageMeter, UsageRecord> lastPolled = new EnumMap<>(UsageMeter.class);
        for (UsageRecord record : records) {
            for (UsageLine line : record.getLines()) {
                if (line.getMeter().getMeasurementType() == UsageMeasurementType.POLLED_LEVEL) {
                    lastPolled.merge(line.getMeter(), record, (a, b) -> b.getHour().after(a.getHour()) ? b : a);
                }
            }
        }
        lastPolled.forEach((meter, record) -> {
            double level = record.getLines().stream().filter(line -> line.getMeter() == meter).mapToDouble(UsageLine::getQuantity).sum();
            double hours = hoursBetween(record.getHour().toInstant().plus(Duration.ofHours(1)), periodEnd);
            ret.getProjectedUsage().merge(meter, level * hours, Double::sum);
        });
        BigDecimal amount = BigDecimal.ZERO;
        for (PlanComponent component : plan.getComponents()) {
            if (component.getType() == PlanComponentType.FLAT) {
                amount = amount.add(BigDecimal.valueOf(component.getAmount()));
            } else {
                double beyondIncluded = ret.getProjectedUsage().getOrDefault(component.getMeter(), 0.0) - component.getIncludedQuantity();
                if (beyondIncluded > 0) {
                    amount = amount.add(component.getUnitAmount().multiply(BigDecimal.valueOf(beyondIncluded)));
                }
            }
        }
        return ret.setProjectedAmount(amount.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private static double hoursBetween(Instant from, Instant to) {
        return Math.max(Duration.between(from, to).toMillis(), 0) / (double) Duration.ofHours(1).toMillis();
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static BillingInvoice invoice(Invoice invoice) {
        return new BillingInvoice().setId(invoice.getId())
                                   .setNumber(invoice.getNumber())
                                   .setStatus(invoice.getStatus() != null ? InvoiceStatus.valueOf(invoice.getStatus().toUpperCase()) : null)
                                   .setCurrency(invoice.getCurrency())
                                   .setTotal(orZero(invoice.getTotal()))
                                   .setAmountPaid(orZero(invoice.getAmountPaid()))
                                   .setAmountDue(orZero(invoice.getAmountDue()))
                                   .setCreated(dateOf(invoice.getCreated()))
                                   .setPeriodStart(dateOf(invoice.getPeriodStart()))
                                   .setPeriodEnd(dateOf(invoice.getPeriodEnd()))
                                   .setHostedInvoiceUrl(invoice.getHostedInvoiceUrl())
                                   .setInvoicePdf(invoice.getInvoicePdf());
    }

    private static long orZero(Long value) {
        return value != null ? value : 0;
    }

    private static Date dateOf(Long epochSecond) {
        return epochSecond != null ? Date.from(Instant.ofEpochSecond(epochSecond)) : null;
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

    /**
     * Deactivates every organization whose payment has been due for longer than the 14-day grace period, and
     * reactivates every deactivated organization whose payment is no longer due. Every organization's account is read,
     * so a trial that ended with no one reading the account starts its grace period. Each pass also suspends the
     * microservices and sites of a deactivated organization still served, and resumes the suspended ones of an active
     * organization.
     * The system organization is never deactivated.
     */
    public Future<Void> enforcePayment() {
        return CursorPaging.forEachPage(organizationService::findAll, PAGE_SIZE, Sort.by("id"),
                                        organizations -> Future.all(organizations.stream()
                                                                                 .filter(organization -> !DomainUtil.SYSTEM_ID.equals(organization.getId()))
                                                                                 .map(this::enforcePayment)
                                                                                 .toList())
                                                               .mapEmpty())
                           .compose(v -> resumeDeployments());
    }

    private Future<Void> enforcePayment(Organization organization) {
        return findAccount(organization.getId()).compose(account -> {
            Future<Void> ret;
            if (overdue(account)) {
                ret = deactivate(organization.getId());
            } else {
                ret = organization.getDeactivated() != null ? reactivate(organization.getId()) : Future.succeededFuture();
            }
            return ret;
        });
    }

    private static boolean overdue(BillingAccount account) {
        return account.getStatus() == BillingStatus.PAST_DUE
                && account.getPastDueSince().toInstant().plus(GRACE_PERIOD).isBefore(Instant.now());
    }

    // suspends the microservices and sites of an organization deactivated already too, so a suspension that failed is retried
    private Future<Void> deactivate(String organizationId) {
        return organizationService.deactivate(organizationId).compose(changed -> {
            if (changed) {
                log.info("Deactivated organization {}, whose payment has been due for longer than {} days", organizationId, GRACE_PERIOD.toDays());
            }
            return renewDeployments(organizationId, deployment -> !suspended(deployment), "organization deactivated");
        });
    }

    private Future<Void> reactivate(String organizationId) {
        return organizationService.reactivate(organizationId).compose(changed -> {
            Future<Void> ret = Future.succeededFuture();
            if (changed) {
                log.info("Reactivated organization {}, whose payment is no longer due", organizationId);
                ret = renewDeployments(organizationId, DefaultBillingOperationsService::suspended, "organization reactivated");
            }
            return ret;
        });
    }

    // the renewed intent has each deployment's worker suspend or resume its VM or site as the organization now is
    private Future<Void> renewDeployments(String organizationId, Predicate<Reconcilable<DeploymentState>> renewed, String source) {
        return Future.all(renew(microserviceDeploymentRepository, organizationId, renewed, source),
                          renew(uiDeploymentRepository, organizationId, renewed, source))
                     .mapEmpty();
    }

    private <T extends Reconcilable<DeploymentState> & OrganizationScoped<String>> Future<Void> renew(AbstractDeploymentRepository<T> repository,
                                                                                                    String organizationId,
                                                                                                    Predicate<Reconcilable<DeploymentState>> renewed,
                                                                                                    String source) {
        return CursorPaging.forEachPage(pageable -> repository.findAllForOrganization(organizationId, pageable),
                                        PAGE_SIZE,
                                        Sort.by("id"),
                                        deployments -> Future.all(deployments.stream()
                                                                             .filter(deployment -> deployment.getState().getDesired() != null && renewed.test(deployment))
                                                                             .map(deployment -> repository.renewDesired(deployment.getId(), source))
                                                                             .toList())
                                                             .mapEmpty());
    }

    // resumes the microservices and sites left suspended by a reactivation whose renewal failed
    private Future<Void> resumeDeployments() {
        Map<String, Future<Organization>> organizations = new HashMap<>();
        return resume(microserviceDeploymentRepository, organizations).compose(v -> resume(uiDeploymentRepository, organizations));
    }

    private <T extends Reconcilable<DeploymentState> & OrganizationScoped<String>> Future<Void> resume(AbstractDeploymentRepository<T> repository,
                                                                                                     Map<String, Future<Organization>> organizations) {
        return CursorPaging.forEachPage(repository::findSuspended, PAGE_SIZE, Sort.by("id"),
                                        deployments -> Future.all(deployments.stream().map(deployment -> organizations
                                                .computeIfAbsent(deployment.getOrganizationId(), organizationService::findById)
                                                .compose(organization -> deployment.getState().getDesired() != null
                                                                         && organization != null
                                                                         && organization.getDeactivated() == null
                                                        ? repository.renewDesired(deployment.getId(), "organization reactivated")
                                                        : Future.succeededFuture()))
                                                .toList())
                                                             .mapEmpty());
    }

    private static boolean suspended(Reconcilable<DeploymentState> deployment) {
        DeploymentState observed = deployment.getState().getObserved();
        return observed != null && observed.phase() == DeploymentStatusType.SUSPENDED;
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
                    // read the subscription again on every attempt, so a slower read of an older state
                    // cannot overwrite the state a later event already saved
                    synced = change(organizationId, account -> stripeBilling.findSubscription(subscriptionId)
                                                                            .map(current -> apply(account, current)))
                            .compose(account -> overdue(account) ? Future.succeededFuture() : reactivate(organizationId));
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
            case "active", "trialing" -> recordPeriod(account, subscription, null).setStatus(BillingStatus.ACTIVE)
                                                .setStripeSubscriptionId(subscription.getId())
                                                .setStripeCheckoutSessionId(null)
                                                .setPlanId(subscription.getMetadata().get(StripeBilling.PLAN_ID_METADATA))
                                                .setCurrentPeriodStart(periodBound(subscription, SubscriptionItem::getCurrentPeriodStart))
                                                .setCurrentPeriodEnd(periodBound(subscription, SubscriptionItem::getCurrentPeriodEnd))
                                                .setPastDueSince(null);
            case "past_due", "unpaid" -> recordPeriod(account, subscription, null).setStatus(BillingStatus.PAST_DUE)
                                                .setStripeSubscriptionId(subscription.getId())
                                                .setStripeCheckoutSessionId(null)
                                                .setPlanId(subscription.getMetadata().get(StripeBilling.PLAN_ID_METADATA))
                                                .setCurrentPeriodStart(periodBound(subscription, SubscriptionItem::getCurrentPeriodStart))
                                                .setCurrentPeriodEnd(periodBound(subscription, SubscriptionItem::getCurrentPeriodEnd))
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
        Instant ended = subscription.getEndedAt() != null ? Instant.ofEpochSecond(subscription.getEndedAt()) : now;
        recordPeriod(account, subscription, Date.from(ended));
        // an event about a subscription the account has since replaced leaves the rest of the account alone
        if (account.getStripeSubscriptionId() == null || account.getStripeSubscriptionId().equals(subscription.getId())) {
            account.setStripeSubscriptionId(null)
                   .setPlanId(null)
                   .setCurrentPeriodStart(null)
                   .setCurrentPeriodEnd(null);
            if (account.getTrialEndsAt().toInstant().isAfter(now)) {
                account.setStatus(BillingStatus.TRIALING).setPastDueSince(null);
            } else {
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
    // a change that another write, from this system server or another, lands before is applied again to the account as that write left it
    private Future<BillingAccount> change(String organizationId, Function<BillingAccount, Future<BillingAccount>> change) {
        return accountOf(organizationId).compose(created -> accountRepository.update(
                organizationId, account -> change.apply(account).map(changed -> changed.setUpdated(new Date()))));
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

    // records the time the subscription covers, keeping the periods of the others until no hour they cover is left to settle
    private static BillingAccount recordPeriod(BillingAccount account, Subscription subscription, Date ended) {
        List<SubscriptionPeriod> periods = account.getSubscriptionPeriods();
        SubscriptionPeriod period = periods.stream()
                                           .filter(candidate -> subscription.getId().equals(candidate.getStripeSubscriptionId()))
                                           .findFirst()
                                           .orElseGet(() -> {
                                               SubscriptionPeriod added = new SubscriptionPeriod().setStripeSubscriptionId(subscription.getId());
                                               periods.add(added);
                                               return added;
                                           });
        period.setStarted(dateOf(subscription.getStartDate())).setEnded(ended);
        Date expired = Date.from(Instant.now().minus(ENDED_PERIOD_RETENTION));
        periods.removeIf(candidate -> candidate.getEnded() != null && candidate.getEnded().before(expired));
        return account;
    }

    // a bound of the current period of the subscription's first item, which every item of a plan's subscription shares
    private static Date periodBound(Subscription subscription, Function<SubscriptionItem, Long> bound) {
        Date ret = null;
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            ret = dateOf(bound.apply(subscription.getItems().getData().getFirst()));
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
