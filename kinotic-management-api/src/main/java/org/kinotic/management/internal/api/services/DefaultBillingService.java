package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.services.billing.BillingOperationsProxy;
import org.kinotic.management.api.services.billing.BillingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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

    private String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }
}
