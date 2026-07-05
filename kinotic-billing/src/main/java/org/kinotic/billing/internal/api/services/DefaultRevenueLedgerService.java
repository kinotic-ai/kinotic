package org.kinotic.billing.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.model.EarningsStatement;
import org.kinotic.billing.api.model.LedgerBalance;
import org.kinotic.billing.api.model.LedgerEntry;
import org.kinotic.billing.api.services.RevenueLedgerService;
import org.kinotic.billing.internal.api.repositories.LedgerEntryRepository;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultRevenueLedgerService extends AbstractOrganizationScopedService<LedgerEntry>
        implements RevenueLedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public DefaultRevenueLedgerService(LedgerEntryRepository ledgerEntryRepository,
                                       SecurityContext securityContext) {
        super(ledgerEntryRepository, securityContext);
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public CompletableFuture<LedgerBalance> balanceFor(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        if (!securityContext.isElevatedAccess()) {
            securityContext.requireAuthScope(AuthScopeType.ORGANIZATION, organizationId);
        }
        return ledgerEntryRepository.sumAmountsByEntryType(organizationId)
                                    .thenApply(sums -> LedgerBalanceCalculator.fromSums(organizationId, sums));
    }

    @Override
    public CompletableFuture<EarningsStatement> statementFor(String organizationId,
                                                             Date periodStart,
                                                             Date periodEnd) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "statementFor is not implemented yet — statements land in the dashboards round"));
    }
}
