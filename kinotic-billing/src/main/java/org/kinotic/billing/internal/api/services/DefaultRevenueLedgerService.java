package org.kinotic.billing.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.model.LedgerBalance;
import org.kinotic.billing.api.services.RevenueLedgerService;
import org.kinotic.billing.internal.api.repositories.LedgerEntryRepository;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultRevenueLedgerService implements RevenueLedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final SecurityContext securityContext;

    @Override
    public CompletableFuture<LedgerBalance> balanceFor(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Participant participant = securityContext.requireParticipant(Participant.class);
        if (participant instanceof OrganizationParticipant orgParticipant) {
            if (!orgParticipant.getOrganizationId().equals(organizationId)) {
                throw new AuthorizationException(
                        "Cannot read the ledger balance of organization '" + organizationId
                        + "' while authenticated as organization '"
                        + orgParticipant.getOrganizationId() + "'");
            }
        } else if (!(participant instanceof SystemParticipant)) {
            throw new AuthorizationException(
                    "Ledger balances are readable only by organization or system participants");
        }
        return ledgerEntryRepository.sumAmountsByEntryType(organizationId)
                                    .thenApply(sums -> LedgerBalanceCalculator.fromSums(organizationId, sums));
    }
}
