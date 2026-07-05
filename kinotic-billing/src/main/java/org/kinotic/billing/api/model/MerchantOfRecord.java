package org.kinotic.billing.api.model;

/**
 * Which entity is the merchant of record for an organization's end-user charges. Determines
 * the payment rail: {@link #KINOTIC} uses platform-account charges with ledger-computed
 * transfers; {@link #ORGANIZATION} is the future direct-charge rail for large organizations
 * running their own merchant account.
 */
public enum MerchantOfRecord {
    KINOTIC,
    ORGANIZATION
}
