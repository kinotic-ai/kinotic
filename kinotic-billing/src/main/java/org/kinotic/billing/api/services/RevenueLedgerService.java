package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.EarningsStatement;
import org.kinotic.billing.api.model.LedgerBalance;
import org.kinotic.billing.api.model.LedgerEntry;
import org.kinotic.core.api.crud.IdentifiableCrudService;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Read side of the revenue-share ledger: derived balances and period statements.
 */
public interface RevenueLedgerService extends IdentifiableCrudService<LedgerEntry, String> {

    /**
     * Returns the organization's current derived balances. Callers must be authenticated
     * under the organization's scope unless running with elevated access.
     */
    CompletableFuture<LedgerBalance> balanceFor(String organizationId);

    /**
     * Returns the organization's statement for the given period: opening/closing balances
     * plus the splits and entries that explain the difference.
     */
    CompletableFuture<EarningsStatement> statementFor(String organizationId, Date periodStart, Date periodEnd);
}
