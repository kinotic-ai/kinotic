-- Stripe merchant-of-record billing (kinotic-billing module). See docs/stripe-connect-billing.md.

-- Idempotency + audit record per Stripe webhook delivery. id = Stripe event id, so a
-- redelivery maps onto the same document. payload is the raw request body, retained for
-- dead-letter forensics and replay.
CREATE TABLE IF NOT EXISTS kinotic_stripe_webhook_event (
    id KEYWORD,
    eventType KEYWORD,
    stripeAccountId KEYWORD,
    livemode BOOLEAN,
    relatedObjectId KEYWORD,
    payload TEXT NOT INDEXED,
    receivedAt DATE,
    processedAt DATE,
    status KEYWORD,
    lastError TEXT NOT INDEXED
);

-- Append-only journal of money movements on each organization's payable balance.
-- Balances are derived by aggregation over amount grouped by entryType — never stored.
-- id = stripeObjectId + ":" + entryType (deterministic, replay-safe).
CREATE TABLE IF NOT EXISTS kinotic_ledger_entry (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    entryType KEYWORD,
    amount LONG,
    currency KEYWORD,
    stripeObjectId KEYWORD,
    revenueSplitId KEYWORD,
    payoutId KEYWORD,
    memo TEXT,
    created DATE
);

-- Per-charge split explanation: amountTotal − taxAmount − stripeFeeAmount − platformFeeAmount
-- = netToOrganization. id = Stripe charge id (naturally idempotent).
CREATE TABLE IF NOT EXISTS kinotic_revenue_split (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    tenantId KEYWORD,
    stripePaymentIntentId KEYWORD,
    stripeInvoiceId KEYWORD,
    endUserStripeCustomerId KEYWORD,
    amountTotal LONG,
    taxAmount LONG,
    stripeFeeAmount LONG,
    platformFeeAmount LONG,
    platformFeeBasisPoints LONG,
    pricingPlanId KEYWORD,
    netToOrganization LONG,
    status KEYWORD,
    created DATE,
    updated DATE
);

-- One billing profile per organization (id == organizationId): payment rail, Stripe
-- recipient account, pricing plan, payout schedule, reserve policy.
CREATE TABLE IF NOT EXISTS kinotic_organization_billing_profile (
    id KEYWORD,
    organizationId KEYWORD,
    merchantOfRecord KEYWORD,
    stripeRecipientAccountId KEYWORD,
    recipientAccountStatus KEYWORD,
    pricingPlanId KEYWORD,
    payoutInterval KEYWORD,
    minimumPayoutAmount LONG,
    reserveBasisPoints LONG,
    reserveHoldDays INTEGER,
    billingStatus KEYWORD,
    created DATE,
    updated DATE
);

-- One batched transfer of earned balance to an organization's recipient account.
-- id doubles as the Stripe idempotency key and transfer_group.
CREATE TABLE IF NOT EXISTS kinotic_payout (
    id KEYWORD,
    organizationId KEYWORD,
    amount LONG,
    currency KEYWORD,
    stripeTransferId KEYWORD,
    cutoffDate DATE,
    status KEYWORD,
    created DATE,
    updated DATE
);
