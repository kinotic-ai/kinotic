package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingForecast;
import org.kinotic.management.api.model.billing.BillingInvoice;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.model.billing.UsageMeterDescriptor;
import org.kinotic.management.api.model.billing.UsageRecord;
import org.kinotic.management.api.services.billing.BillingOperationsProxy;
import org.kinotic.management.api.services.billing.BillingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DefaultBillingService implements BillingService {

    private final BillingOperationsProxy operations;
    private final SecurityContext securityContext;

    @Override
    public Future<BillingAccount> findAccount() {
        return operations.findAccount(requireOrganizationId());
    }

    @Override
    public Future<List<BillingPlan>> findPlans() {
        return operations.findPlans(requireOrganizationId());
    }

    @Override
    public Future<String> createCheckoutSession(String planId) {
        return operations.createCheckoutSession(requireOrganizationId(), planId);
    }

    @Override
    public Future<String> createPortalSession() {
        return operations.createPortalSession(requireOrganizationId());
    }

    @Override
    public Future<List<UsageMeterDescriptor>> findMeters() {
        return Future.succeededFuture(Arrays.stream(UsageMeter.values())
                                            .map(meter -> new UsageMeterDescriptor().setMeter(meter)
                                                                                    .setDisplayName(meter.getDisplayName())
                                                                                    .setUnit(meter.getUnit())
                                                                                    .setCategory(meter.getCategory())
                                                                                    .setMeasurementType(meter.getMeasurementType()))
                                            .toList());
    }

    @Override
    public Future<List<UsageRecord>> findUsage(Date from, Date to) {
        return operations.findUsage(requireOrganizationId(), from, to);
    }

    @Override
    public Future<BillingForecast> findForecast() {
        return operations.findForecast(requireOrganizationId());
    }

    @Override
    public Future<List<BillingInvoice>> findInvoices() {
        return operations.findInvoices(requireOrganizationId());
    }

    private String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }
}
