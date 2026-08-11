package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.LedgerBalance;

import java.util.concurrent.CompletableFuture;

/**
 * Read side of the revenue-share ledger. Deliberately exposes no generic CRUD: ledger
 * entries are append-only and are posted exclusively through {@link RevenueSplitService}.
 */
public interface RevenueLedgerService {

    /**
     * Returns the organization's current derived balances. Organization participants may
     * only read their own balance; system participants may read any organization's.
     */
    CompletableFuture<LedgerBalance> balanceFor(String organizationId);
}
