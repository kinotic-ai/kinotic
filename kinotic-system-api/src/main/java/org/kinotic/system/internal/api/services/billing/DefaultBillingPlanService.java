package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.PlanComponent;
import org.kinotic.management.api.model.billing.PlanComponentType;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.system.api.services.billing.BillingPlanService;
import org.kinotic.system.internal.api.repositories.BillingPlanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DefaultBillingPlanService implements BillingPlanService {

    private static final Pattern CURRENCY = Pattern.compile("[a-z]{3}");
    // Stripe takes at most 12 decimal places in unit_amount_decimal
    private static final int MAX_UNIT_AMOUNT_SCALE = 12;

    private final BillingPlanRepository planRepository;
    private final OrganizationService organizationService;
    private final StripeBilling stripeBilling;

    @Override
    public Future<Page<BillingPlan>> findAll(Pageable pageable) {
        return planRepository.findAll(pageable);
    }

    @Override
    public Future<BillingPlan> findById(String id) {
        return planRepository.findById(id);
    }

    @Override
    public Future<BillingPlan> create(BillingPlan plan) {
        validate(plan);
        Date now = new Date();
        plan.setId(UUID.randomUUID().toString())
            .setArchived(false)
            .setCreated(now)
            .setUpdated(now);
        return requireOrganization(plan.getOrganizationId())
                .compose(v -> stripeBilling.createProduct(plan))
                .compose(planRepository::createSync);
    }

    @Override
    public Future<BillingPlan> archive(String id) {
        return planRepository.findById(id).compose(plan -> {
            Future<BillingPlan> ret;
            if (plan == null) {
                ret = Future.failedFuture(new IllegalArgumentException("No billing plan has the id " + id));
            } else if (plan.isArchived()) {
                ret = Future.succeededFuture(plan);
            } else {
                ret = stripeBilling.archiveProduct(plan.getStripeProductId())
                                   .compose(v -> planRepository.saveSync(plan.setArchived(true).setUpdated(new Date())));
            }
            return ret;
        });
    }

    private Future<Void> requireOrganization(String organizationId) {
        Future<Void> ret;
        if (organizationId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = organizationService.findById(organizationId)
                                     .compose(organization -> organization != null
                                             ? Future.succeededFuture()
                                             : Future.failedFuture(new IllegalArgumentException("No organization has the id " + organizationId)));
        }
        return ret;
    }

    private static void validate(BillingPlan plan) {
        Validate.notNull(plan, "The plan is required");
        Validate.notBlank(plan.getName(), "The plan's name is required");
        Validate.isTrue(plan.getCurrency() != null && CURRENCY.matcher(plan.getCurrency()).matches(),
                        "The plan's currency must be a three-letter lowercase ISO 4217 code, such as usd");
        Validate.notNull(plan.getInterval(), "The plan's interval is required");
        Validate.notEmpty(plan.getComponents(), "The plan needs at least one component");
        Set<UsageMeter> metered = EnumSet.noneOf(UsageMeter.class);
        for (PlanComponent component : plan.getComponents()) {
            Validate.notNull(component.getType(), "Every component's type is required");
            if (component.getType() == PlanComponentType.FLAT) {
                Validate.isTrue(component.getAmount() != null && component.getAmount() >= 0,
                                "A FLAT component's amount is required and not negative");
                Validate.isTrue(component.getMeter() == null, "A FLAT component meters nothing");
            } else {
                Validate.notNull(component.getMeter(), "A METERED component's meter is required");
                Validate.isTrue(metered.add(component.getMeter()), "The plan meters %s twice", component.getMeter());
                BigDecimal unitAmount = component.getUnitAmount();
                Validate.isTrue(unitAmount != null && unitAmount.signum() >= 0
                                        && unitAmount.stripTrailingZeros().scale() <= MAX_UNIT_AMOUNT_SCALE,
                                "A METERED component's unit amount is required, not negative, and has at most %d decimal places",
                                MAX_UNIT_AMOUNT_SCALE);
                Validate.isTrue(component.getIncludedQuantity() >= 0, "A METERED component's included quantity is not negative");
            }
        }
    }
}
