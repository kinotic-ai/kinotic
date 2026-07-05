package org.kinotic.billing.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.model.LedgerEntryType;

/**
 * Builds the deterministic ids that make ledger writes replay-safe: the same source object
 * and entry type always produce the same id, so a webhook redelivery or scheduler re-run
 * overwrites the identical entry instead of duplicating it.
 */
public final class LedgerEntryIds {

    /**
     * @return {@code stripeObjectId + ":" + entryType.name()}
     */
    public static String entryId(String stripeObjectId, LedgerEntryType entryType) {
        Validate.notBlank(stripeObjectId, "stripeObjectId cannot be blank");
        Validate.notNull(entryType, "entryType cannot be null");
        return stripeObjectId + ":" + entryType.name();
    }

    private LedgerEntryIds() {
    }
}
