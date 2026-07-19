# Kinotic Billing — Kinotic as Merchant of Record — Design

**Status:** Draft for review. **Direction decided: Kinotic is the merchant of record (MoR) for the MVP launch** (decision record in §4). Open decisions that need answers before implementation are in §11.
**Date:** 2026-07-04
**Author:** Nic Padilla (discovery sessions with Claude)

---

## 1. Terminology

| Term | Meaning |
|---|---|
| **Kinotic** | The platform and the **merchant of record**. Owns the Stripe platform account; every End-user transaction is legally with Kinotic. |
| **Customer** | An Organization on Kinotic (an Application owner). Becomes a Stripe **recipient-agreement connected account** — a payout destination, not a merchant. |
| **End-user** | A user of a Customer's Application. Pays **Kinotic**; exists as a Stripe `Customer` object on Kinotic's platform account. |

Hierarchy (per CLAUDE.md): **Org → Application → end-user data (partitioned by tenant)**. The recipient account attaches at the **Org** level — one Org = one recipient account = one bank account = one payout stream, shared across all of that Org's Applications.

## 2. Goals

1. Customers monetize their Applications through Kinotic: fixed subscriptions, per-seat, one-time purchases, and usage-based billing.
2. Kinotic collects all End-user revenue, takes its cut, and pays each Customer the remainder — the platform bill is netted from proceeds by construction.
3. Kinotic **always** gets paid: fee netting happens before money moves, including under refunds and disputes.
4. Full transparency: Customers see the same usage metrics and revenue math that drive their earnings and Kinotic's cut.
5. **Legitimacy of the cloud OS**: Kinotic on every statement, receipt, and checkout — commerce is a platform capability, like CI/CD and deployment. The Customer pitch: *"monetize your Application with zero payments paperwork — connect a bank account and get paid."*
6. Preserve a **graduation path** for large Organizations to become their own MoR later without re-architecting (§4.3).

## 3. Decision summary

| Decision | Choice | Notes |
|---|---|---|
| Merchant of record | **Kinotic** (decision record §4) | Customer-own-MoR preserved as a later graduation path |
| Charge rail | **Separate charges and transfers (SCT)**: all End-user charges on the platform account; ledger computes shares; **batched transfers** on the payout schedule | Destination charges rejected as primary rail — per-charge transfers forfeit batching (payout fees are per payout) and bypass the ledger, which we need anyway. OPEN §11-D1 to confirm |
| Customer account shape | **Accounts v2**, `recipient` configuration, recipient service agreement, `dashboard: none` | Transfers-only capability, light KYC, no Stripe relationship — white-label |
| Liability posture | `fees_collector: application`, `losses_collector: application` (forced by recipient/none-dashboard) | No Stripe loss backstop — mitigated by holding funds + reserves (§9) |
| Split source of truth | **Kinotic revenue-share ledger** (§7) — append-only journal; Stripe is the money rail | |
| End-user billing stack | Stripe Billing on the **platform account**: Products/Prices/Subscriptions/Invoices/Meters, End-user `Customer` objects, partitioned by metadata (`orgId`/`applicationId`/`tenantId`) | The metadata contract is the attribution root — treat as immutable API |
| Webhook payloads | **Thin events** via v2 EventDestinations; handlers re-fetch authoritative state | Mostly a single platform stream now (§10) |
| Usage source of truth | Kinotic's time-series store (Elasticsearch); Stripe Billing Meters is the billing projection | All meter events target the platform account — no `Stripe-Account` fan-out |
| Tax | Kinotic is the taxable seller; **Stripe Tax** on the platform account for calc/collection; filing via partners | Registration strategy OPEN §11-B4 |
| Monetization gating | Charging End-users is a **reviewed capability** granted through the existing application review pipeline | §8 |

## 4. Decision record: Kinotic as MoR

### 4.1 Why

- **Legitimacy.** End-users transact with Kinotic: `KINOTIC* <APP>` statement descriptors, Kinotic receipts and invoices, one trusted billing entity across every Application — the App Store / Steam model, and what makes "the OS handles commerce" credible.
- **Customer onboarding collapses.** Recipient accounts need only light identity + a bank account — no merchant KYC, no tax IDs and beneficial-owner ceremony. Minutes, not days; Customers never see Stripe.
- **"Cut from proceeds" becomes structural.** Kinotic holds all funds, so goals 2–3 are a ledger subtraction before transfer, not a two-account reconciliation.
- **Money-transmission is largely covered.** Within Connect, Stripe holds the funds and extends its MTL (US) / e-money (EU) licenses to platforms; Kinotic does not pursue state licensing. Counsel confirmation is open item §11-B1.
- **Platform-control synergy.** Kinotic already owns the whole application lifecycle — CI/CD, vuln scans, SBOM, compliance checks, review before build and publish, fully sandboxed runtime. The card-network obligation to answer for what Applications sell is **not a new process**: monetization review is one more gate in the existing pipeline (§8), and total visibility into app source/SBOM/runtime is stronger fraud-and-dispute posture than any marketplace hosting opaque third-party code.
- **Multi-app bundling** (one End-user invoice spanning Applications, N-way splits) is only possible with the platform collecting — this rail enables it even if deferred past MVP.

### 4.2 What we accept

| Obligation | Detail | Mitigation |
|---|---|---|
| Disputes | Every End-user chargeback debits Kinotic; no Stripe loss backstop | Radar for Platforms; evidence workflows pulling context from Customers; ledger clawback + reserves (§9) |
| Tax | Kinotic is the taxable seller; all End-user sales across all Applications aggregate toward **Kinotic's** economic nexus → registration thresholds arrive fast | Stripe Tax + filing partners; tax codes assigned per Application product at review; launch-geo limits |
| 1099-K | Kinotic issues 1099-Ks to Customers (platform-paid fees) | Stripe 1099 product (~$2.99/form); W-9/TIN collection at Customer onboarding |
| Card-network compliance | MCC accuracy; MoR identity unambiguous in storefront/TOS/receipts; prohibited categories are our violation | Monetization review gate (§8); digital services only at launch |
| Refund policy | A Kinotic-level policy governs all Applications | Platform-wide policy with per-app parameters (§11-A5) |
| Unit economics | We pay 2.9% + 30¢ processing, $2/monthly-active account, 0.25% + 25¢ per payout, dispute fees, tax/1099 ops | Take-rate floor ≈ **5%+ of volume** (or SaaS fee + lower %); benchmark: Stripe Managed Payments charges 7–9% for the same burdens |

### 4.3 Graduation path: a large Org as its own MoR (design-for-later)

Service agreement type is immutable per connected account, so graduation is a **new full-agreement account + drain the old recipient balance** — a designed migration, not a conversion. To keep it cheap later, the MVP makes these choices now:

1. `OrganizationBillingProfile` carries a `merchantOfRecord` enum (`KINOTIC` | `ORGANIZATION`) and the account/agreement shape — nothing assumes "recipient" globally.
2. Charge creation goes through a **payment-rail strategy**: MVP implements the platform-account SCT rail; a direct-charge rail (charges on the org's own account, our cut via `application_fee_*`) slots in later behind the same interface.
3. The **metadata contract** (`orgId`/`applicationId`/`tenantId` on every Stripe object) is identical on both rails — attribution, metering, and dashboards don't care who the MoR is.
4. The **ledger** stays authoritative on both rails: on the Kinotic-MoR rail it drives transfers; on a future org-MoR rail its entries become informational (fees collected via `application_fee_*`) but statements/reporting are unchanged.
5. Kinotic's **plan/catalog model is Kinotic-native** and *projected* into Stripe (platform account today; an org's account later) — Customers' pricing definitions never bind to a specific Stripe account.

## 5. Money flow

```
End-user pays (Kinotic-branded checkout, "KINOTIC* <APP>" descriptor)
  └─▶ PaymentIntent on Kinotic platform account   ← Kinotic = MoR
        └─▶ charge.succeeded → RevenueSplit (§7):
              amountTotal − tax (remitted) − Stripe fee − Kinotic fee = net to Org
              → EARNING entry on the Org's ledger
Payout scheduler (per Org: interval, minimum, reserve, hold days)
  └─▶ one batched Stripe transfer → Org's recipient account (+24 h availability)
        └─▶ Stripe pays out to the Org's bank
```

- **Floor / minimum platform fee** for zero-revenue Orgs is netted in the ledger when earnings exist (`PLATFORM_FEE_FLOOR` entry); when they don't, it's charged to the Org's card on file — the only surviving remnant of "Kinotic invoices the Customer."
- **Batching matters:** payout fees (0.25% + 25¢) are per payout, so transfers batch on the payout schedule rather than per charge — cheaper and one bank line per period for the Customer.
- **Async payment safety:** earnings post only from `charge.succeeded` with a resolved balance transaction; Stripe does **not** auto-reverse SCT transfers when an ACH/SEPA payment later fails, so nothing transfers before settlement + hold period.
- Refunds and disputes claw back through the ledger (§7, §9) — the Customer's share absorbs them by default; Kinotic's fee retention on refunds is a policy flag (§11-A2).

## 6. Accounts: recipient accounts on Accounts v2

Per-Org account:

```jsonc
POST /v2/core/accounts        // pin Stripe-Version on every v2 call
{
  "display_name": "<org name>",
  "contact_email": "<org billing email>",
  "dashboard": "none",
  "identity": { "country": "us", ... },                    // light identity for recipient agreement
  "configuration": { "recipient": { /* stripe_balance.stripe_transfers */ } },
  "defaults": {
    "currency": "usd",
    "responsibilities": { "fees_collector": "application", "losses_collector": "application" }
  },
  "include": ["configuration.recipient", "identity", "requirements"]
}
```

- **Onboarding:** create account → collect bank + identity via **embedded onboarding** (Stripe's implementation planner explicitly recommends embedded over hosted-link or API onboarding — it adapts to new/country-specific requirements automatically, avoiding custom remediation flows; hosted account links are single-use and expire within minutes, so the hosted path additionally requires a link-refresh endpoint — spike-verified) → `account.updated` advances status → transfers enabled. **Include the notification-banner embedded component** in the org's billing UI: with `dashboard: none`, Stripe does not chase updated KYC on its own, so the banner is how ongoing requirement changes reach the Customer. W-9/TIN collection for 1099 happens here.
- **Do not request the `merchant` configuration or payment-method capabilities on recipient accounts** — recipients don't accept payments, and requesting merchant capabilities triggers materially longer onboarding (per Stripe's planner). Payment-method capabilities belong on the platform account only.
- **Gate transfers on `configuration.recipient.capabilities.stripe_balance.stripe_transfers.status == "active"`** — checked before every payout sweep (§7.4 eligibility). Failure mode is `insufficient_capabilities_for_transfer` (spike-verified).
- **Transfers and payouts have separate requirement tiers** (spike run 2 finding): `stripe_transfers` activates with just registered name + business URL + ToS acceptance; `external_account` (bank) and `us_ein` (only *eventually due*) gate `stripe_balance.payouts` only. A recipient can therefore receive transfers into their Stripe balance before adding a bank account. Track both capability states on `OrganizationBillingProfile` (not a single status), and decide policy: hold payout sweeps until `payouts` is active, or transfer early and let funds wait in their Stripe balance.
- **Validated recipient KYC surface (US company, spike run 2):** registered name, business URL, ToS acceptance, bank account; EIN eventually-due; **no representative SSN/DOB, no beneficial-owner collection** in the initial requirement set. The "connect a bank account" onboarding pitch (§4.1) is literal.
- Customers have **no Stripe relationship** (recipient agreement: their contract is with Kinotic) and no Stripe Dashboard; earnings/payout/bank UI is Kinotic's.
- Transfers to recipient accounts take **+24 h** to become available (spike-verified: transferred funds land in the recipient's `pending` balance) — payout latency, not a money-loss risk.
- v2 sharp edges: responses return `null` for anything not in `include` (wrap in a Kinotic `StripeClient` facade that always passes our list); SDK splits across `/v2/core` and v1 namespaces; no OAuth on v2 (irrelevant — Kinotic creates all accounts).

## 7. Revenue-share ledger

The system of record for *what Kinotic owes each Organization*. Lives in `kinotic-domain`: entities in `api/model/billing/`, interfaces in `api/services/billing/`, impls in `internal/api/services/billing/`, repositories in `internal/api/repositories/billing/` (subpackage justified by file count, mirroring `iam`).

### 7.1 Design principles

1. **Append-only journal.** `LedgerEntry` rows are never mutated; corrections are new `ADJUSTMENT` entries with a memo. Balances are **derived** by aggregation over entries — never a stored counter — so there is no dual-write consistency problem on Elasticsearch and the ledger is always recomputable and auditable.
2. **Deterministic entry IDs ⇒ idempotency for free.** Entry `id` derives from source (`{stripeObjectId}:{entryType}`); webhook replays and scheduler re-runs are create-only no-ops. Same trick as meter-event `identifier` dedupe (§8.2 of the metering section).
3. **Split vs entry: transparency vs money.** `RevenueSplit` is the per-charge *explanation* (gross, tax, Stripe fee, Kinotic fee, net) that a Customer drills into; `LedgerEntry` is the resulting *money movement*. One split → one `EARNING` entry (plus later clawbacks), linked by id.
4. **Attribution via the metadata contract.** Every Stripe object Kinotic creates carries `orgId`/`applicationId`/`tenantId` metadata.
5. **Money as `long` minor units; single currency (`usd`) per org at launch.**
6. **Org-scoped reads, elevated writes.** Entities implement `OrganizationScoped`; org admins see only their own ledger through the standard scoped CRUD path; writes originate from webhook/scheduler system contexts.

### 7.2 Entities (`api/model/billing/`, one file each)

| Entity | Key fields | Notes |
|---|---|---|
| `LedgerEntry` | `id` (deterministic), `organizationId`, `applicationId` (nullable for org-level entries), `entryType`, `amount` (signed), `currency`, `stripeObjectId`, `revenueSplitId` (nullable), `payoutId` (nullable), `memo`, `created` | Append-only journal line |
| `RevenueSplit` | `id` = Stripe charge id (natural key), `organizationId`, `applicationId`, `tenantId`, `stripePaymentIntentId`, `stripeInvoiceId`, `endUserStripeCustomerId`, `amountTotal`, `taxAmount`, `stripeFeeAmount`, `platformFeeAmount`, `platformFeePercent`, `pricingPlanId`, `netToOrganization`, `status`, `created`, `updated` | Invariant: `netToOrganization = amountTotal − taxAmount − stripeFeeAmount − platformFeeAmount`. Tax is remitted, never anyone's revenue |
| `OrganizationBillingProfile` | `id` = org id, `merchantOfRecord` (§4.3), `stripeRecipientAccountId`, `recipientAccountStatus`, `pricingPlanId`, `payoutInterval`, `minimumPayoutAmount`, `reservePercent`, `reserveHoldDays`, `billingStatus` | Separate entity, not fields on `Organization` — different lifecycle and access rules |
| `Payout` | `id` (doubles as Stripe idempotency key and `transfer_group`), `organizationId`, `amount`, `currency`, `stripeTransferId`, `cutoffDate`, `status`, `created`, `updated` | One batched transfer to the org's recipient account |

Enums (own files, per the enums-over-strings rule): `LedgerEntryType` — `EARNING`, `REFUND_CLAWBACK`, `DISPUTE_HOLD`, `DISPUTE_RELEASE`, `DISPUTE_FEE`, `RESERVE_HOLD`, `RESERVE_RELEASE`, `PLATFORM_FEE_FLOOR`, `PLATFORM_FEE_USAGE`, `TRANSFER`, `TRANSFER_REVERSAL`, `ADJUSTMENT`; `RevenueSplitStatus` — `PENDING`, `EARNED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `DISPUTED`, `CLAWED_BACK`; `PayoutStatus` — `PENDING`, `IN_TRANSIT`, `COMPLETED`, `FAILED`, `REVERSED`; `PayoutInterval` — `DAILY`, `WEEKLY`, `MONTHLY`, `MANUAL`; `RecipientAccountStatus` — `NOT_STARTED`, `PENDING_VERIFICATION`, `ACTIVE`, `RESTRICTED`; `OrgBillingStatus` — `ACTIVE`, `PAYOUTS_HELD`, `SUSPENDED`; `MerchantOfRecord` — `KINOTIC`, `ORGANIZATION`.

DTOs (returned by services, not persisted): `LedgerBalance` (`payable`, `reserved`, `disputeHeld`, `availableForPayout`), `EarningsStatement` (period, opening/closing balance, split summaries, entries).

### 7.3 Services (`api/services/billing/`)

```java
public interface RevenueSplitService extends IdentifiableCrudService<RevenueSplit, String> {

    /**
     * Records the revenue split for a settled charge and credits the owning
     * organization's ledger. Idempotent: replaying the same charge is a no-op.
     */
    CompletableFuture<RevenueSplit> recordCharge(String stripeChargeId);

    CompletableFuture<RevenueSplit> recordRefund(String stripeChargeId, String stripeRefundId);

    CompletableFuture<RevenueSplit> recordDisputeOpened(String stripeChargeId, String stripeDisputeId);

    CompletableFuture<RevenueSplit> recordDisputeClosed(String stripeChargeId, String stripeDisputeId);
}
```

```java
public interface RevenueLedgerService extends IdentifiableCrudService<LedgerEntry, String> {

    /** Current derived balances for the organization. */
    CompletableFuture<LedgerBalance> balanceFor(String organizationId);

    /** Customer-facing earnings statement joining entries with their revenue splits. */
    CompletableFuture<EarningsStatement> statementFor(String organizationId, Date periodStart, Date periodEnd);
}
```

```java
public interface PayoutService extends IdentifiableCrudService<Payout, String> {

    /** Sweeps all eligible organizations and initiates transfers. Invoked by the payout scheduler. */
    CompletableFuture<Void> runScheduledPayouts();

    CompletableFuture<Payout> initiatePayout(String organizationId);

    /** Reconciles transfer/payout webhook events into payout status transitions. */
    CompletableFuture<Payout> reconcile(String stripeObjectId);
}
```

Plus `OrganizationBillingProfileService extends IdentifiableCrudService<OrganizationBillingProfile, String>` owning recipient-account lifecycle: create the v2 account with the `recipient` configuration, mint embedded-component sessions for bank/account-management UI, consume `account.updated` to advance `recipientAccountStatus`.

`recordCharge` takes only the charge id (thin-event philosophy, §10): the implementation fetches the authoritative charge + balance transaction from Stripe (the exact `stripeFeeAmount` lives on the balance transaction, only available once the charge settles), resolves org/app from the metadata contract, resolves the fee from the org's pricing plan, then persists the split and posts the `EARNING` entry.

The interfaces above show the full design; per YAGNI each method is **declared only in the round that implements it** — the MVP ships `recordCharge` and `balanceFor`. The refund/dispute methods, `statementFor`, and the `Payout` entity/repository join with their rounds. The ledger and split services deliberately expose **no generic CRUD**: entries and splits are append-only, written exclusively through `recordCharge` — a save/delete surface on the journal would let its own history be rewritten.

### 7.4 Flows

```
charge.succeeded ──▶ RevenueSplitService.recordCharge
                       fetch charge + balance transaction → resolve org/app from metadata
                       → persist RevenueSplit → post EARNING (+net)

charge.refunded ──▶ recordRefund
                       → REFUND_CLAWBACK (−org share; Kinotic fee retained by default, policy flag §11-A2)

charge.dispute.created ──▶ recordDisputeOpened → DISPUTE_HOLD (−) [+ DISPUTE_FEE per policy §11-A3]
charge.dispute.closed  ──▶ recordDisputeClosed → won: DISPUTE_RELEASE (+) / lost: hold stands

payout scheduler ──▶ PayoutService.runScheduledPayouts
                       eligible = billingStatus ACTIVE ∧ recipientAccountStatus ACTIVE
                                  ∧ payable − reserve ≥ minimumPayoutAmount
                                  ∧ entries older than reserveHoldDays
                       → Stripe transfer (idempotency key = payout id, transfer_group = payout id)
                       → post TRANSFER (−), Payout PENDING
transfer/payout webhooks ──▶ PayoutService.reconcile → COMPLETED / FAILED (+ TRANSFER_REVERSAL)
```

Ordering hazards are absorbed by the same machinery as §10: handlers re-fetch authoritative Stripe state, and deterministic ids make replays safe. `EARNING` posts only from `charge.succeeded` with a resolved balance transaction — never from `payment_intent.succeeded` alone — which also covers the async-payment trap (§5).

## 8. Metering pipeline

One pipeline, three consumers: (a) Customer dashboards, (b) End-user usage-based billing, (c) Kinotic's platform-fee computation. All three read the same numbers — the transparency guarantee. **Simplified by the MoR decision:** all meters, prices, and End-user `Customer` objects live on the platform account — no `Stripe-Account` fan-out anywhere.

### 8.1 Metric taxonomy

| Metric | Kind | Stripe meter aggregation | Emission shape |
|---|---|---|---|
| Number of requests | counter | `sum` (batched value) or `count` | per-minute batch per app |
| Bandwidth throughput | counter (bytes) | `sum` | bytes-in and bytes-out as separate meters |
| Records stored | gauge | `last_during_period` | periodic snapshot |
| GB stored | gauge | `last_during_period` | periodic snapshot |
| Number of users | gauge | `last_during_period` | snapshot at period close |
| CPU | time-weighted | `sum` | vCPU-hours (sample × interval), never instantaneous % |
| Memory | time-weighted | `sum` | GB-hours |

Counters accumulate; gauges snapshot; compute is billed as intensity × duration (the cloud-provider model).

### 8.2 Architecture

```
App runtime (sandboxed Firecracker VMs) ─ raw samples ─▶ Metering Ingest (Vert.x)
                                             │  tags: orgId, applicationId, tenantId
                         ┌───────────────────┴──────────────────┐
                         ▼                                      ▼
              Time-series store (ES)                  Billing Aggregator (scheduled)
                         │                              hourly/daily rollups
                         ▼                                      │
              Customer dashboards                               ▼
              - app usage, per-End-user breakdown     Stripe Billing Meters (platform account)
              - live earnings / Kinotic-cut preview     - End-user metered subscriptions
                                                        - platform-fee usage components
                                                      → invoices on cycle close → §7 ledger
```

- **Sample high-resolution at the source, aggregate before Stripe.** Stripe gets hourly/daily rollups; our store keeps full fidelity so we can re-bill if meter config changes.
- **Deterministic dedupe.** Meter events carry `identifier = {orgId}-{meter}-{timeBucket}-{shard}`; Stripe drops duplicates (~24 h window), so the aggregator can re-emit any window after a failure.
- **Dashboards never read usage from Stripe** (async aggregation lag); our store is the observability source of truth, Stripe the billing projection. Fetch Stripe's upcoming-invoice endpoint only as the "next invoice estimate" cross-check.
- **Meter health:** `v1.billing.meter.error_report_triggered` → ops alert; rejected meter events are silently dropped revenue otherwise.
- **Drill-down to raw events** in the Customer dashboard: "you earned on 1.2 M requests — here's the per-minute series that sums to it."
- The **metric event schema** emitted by Application runtimes is the longest-lived contract in this design — it gets its own short design pass first (§11-D3).

### 8.3 From usage to money

There are no real-time "cost events." Resource utilization is **always** recorded as observability data first (the time-series store); it becomes money only through the **Billing Aggregator's scheduled rollups** (hourly/daily), via two distinct paths:

1. **End-user metered pricing** → the aggregator emits Stripe **meter events** for prices on End-user subscriptions → Stripe invoices at cycle close → `charge.succeeded` → `EARNING` ledger entry. Usage becomes org revenue only through a real Stripe charge.
2. **Kinotic's usage-based platform fee** → the aggregator computes it directly from the same rollups and posts **`PLATFORM_FEE_USAGE` ledger entries** — no Stripe billing involved, because Kinotic already holds the org's funds; the fee is netted at payout like everything else. Only zero-revenue orgs fall back to a card-on-file charge (same fallback as the floor fee, §5).

**Payout runs never compute usage.** `runScheduledPayouts` sweeps already-posted journal entries — a payout is a sum of ledger lines, each traceable back to a specific usage rollup window. That keeps payouts fast, deterministic, and auditable: re-running the aggregator can never change a past payout, and a Customer disputing a number drills from the payout → entries → splits/rollups → raw per-minute series (§8.2). The Customer's billing view reads live from the time-series store (usage) and the ledger (money) — Stripe is consulted only for the upcoming-invoice cross-check.

## 9. Risk management

Kinotic holds the funds, so "Kinotic always gets paid" is structural. The residual risk is outbound: refunds, disputes, and negative org balances.

- **Reserves:** per-org `reservePercent` + `reserveHoldDays` on the billing profile (risk-tiered: conservative for new orgs, relaxed with history). Earnings are transferable only after the hold; a rolling reserve slice stays back.
- **Disputes:** Radar for Platforms on all End-user charges; `DISPUTE_HOLD` freezes the org share immediately; evidence workflows pull context from the Customer (who knows their End-user); dispute-fee allocation is policy §11-A3.
- **Negative org ledger balances** (refund/dispute wave exceeding payable): recovery = offset against future earnings first, then reserve draw, then (policy) invoice the org / suspend. Note this negative balance lives in **our ledger** — the org's Stripe balance only ever holds what we've already transferred.
- **Platform float:** the platform balance funds End-user refunds and dispute debits before clawbacks land; keep a working float — a deeply negative platform balance can suspend platform operations.
- **Transfer reversals** can recover funds from a recipient account after the fact, but cross-border reversals have sharp edges (recovered dispute funds may not be re-transferable) — US-only launch avoids this class (§11-B3).
- **Refund-policy abuse:** monetization review (below) plus platform-wide refund policy bounds what an Application can promise End-users.

**Monetization gate in the review pipeline.** Charging End-users is a reviewed capability, not a default: the existing change-review gate gains a monetization step — category/MCC attestation, tax-code assignment, prohibited-content check, pricing-plan linkage. Passing review is what links an Application into the attribution metadata contract; an unreviewed app has no prices to sell.

## 10. Webhook receiver architecture

Mostly a **single platform stream** now (all charges, invoices, subscriptions, meters are ours), plus recipient-account lifecycle events.

| Endpoint | Carries |
|---|---|
| `POST /webhooks/stripe/platform` | `charge.succeeded/refunded`, `charge.dispute.*`, `invoice.paid/payment_failed`, `customer.subscription.*`, `transfer.*`, `v1.billing.meter.error_report_triggered`, `balance.available` |
| `POST /webhooks/stripe/connected` | `account.updated` (recipient onboarding), `payout.paid/failed` on recipient accounts (Customer-visible payout history) |

Endpoint paths are constants; the per-environment config is the two signing secrets (separate secrets so a leak of one doesn't compromise the other).

Ingest/process split (the hot path must 2xx fast):

```
Stripe ──▶ StripeWebhookVerticle
             1. raw body (Stripe routes exempt from BodyHandler — HMAC needs exact bytes)
             2. verify signature (Stripe Java SDK)
             3. idempotency insert on event.id → conflict = already seen = 200, stop
             4. persist raw event via StripeWebhookEventService (AbstractCrudService subclass)
             5. EventBus.publish → 6. 200
                    ▼
           StripeWebhookProcessor (consumer verticle pool)
             - thin event → fetch related object = authoritative current state
             - dispatch by type → owning service (§7 services, OrganizationBillingProfileService)
             - failure → bounded retry w/ backoff → DEAD_LETTERED + ops alert
```

Idempotency record: `stripe_event_id` (PK), `event_type`, `stripe_account_id` (null = platform), `livemode`, `received_at`, `processed_at`, `status` (`RECEIVED|PROCESSED|FAILED|DEAD_LETTERED`), `last_error`.

Gotchas: `livemode` guard in the dispatcher (Stripe sends test events to live URLs); dead-lettering is ours (Stripe only retries non-2xx, and we 2xx before processing); handlers run with elevated cross-org access (webhooks carry no org security context).

## 11. Open decisions (need answers for this direction)

### A. Business & pricing

1. **Take rate & pricing model.** DECIDED in part: launch includes a **free tier** — a founder can build and vet an idea at no cost, and monetization is introduced as a guided user journey once the Application gains users (the monetization review gate, §9, is the natural entry point to that journey). Still open: the paid take rate (percent-only with ≥5% economic floor §4.2, SaaS + lower percent, or tiered), free-tier resource limits, and what usage/user threshold prompts the monetization journey.
2. **Kinotic fee on refunds.** Retain (consistent with "the compute was consumed"; recommended default) or refund proportionally? Per-plan flag or global?
3. **Dispute fee allocation.** Pass the ~$15 network dispute fee to the org, absorb it, or split? Recommend pass-through with a first-N-forgiven allowance.
4. **Payout defaults & risk tiers.** Interval (recommend weekly default, daily for established orgs), `minimumPayoutAmount`, `reservePercent`, `reserveHoldDays` (recommend 7-day hold, 0–10% rolling reserve by tier).
5. **Platform-wide End-user refund policy.** Window, self-serve vs Customer-approved, and who arbitrates End-user↔Customer refund conflicts (we're the MoR — final say is ours).

### B. Legal & compliance (counsel engagement)

1. **Money-transmission opinion** confirming Kinotic operates under Stripe's licensing umbrella with the SCT flow.
2. **Contract stack:** End-user Terms of Sale (Kinotic as seller, MoR-unambiguous per card-network rules), Customer revenue-share agreement (take rate, payout terms, clawbacks, content policy), platform refund policy, privacy addenda.
3. **Launch geography.** Recommend US-only (both Customers and End-user payment processing) — avoids cross-border transfer-reversal sharp edges and multi-country VAT day-one.
4. **Tax strategy.** Initial state registrations (home state + monitor Stripe Tax thresholds), filing partner selection, digital-services taxability mapping, and the per-product tax-code assignment step in monetization review.
5. **1099-K program.** Stripe 1099 product; W-9/TIN collection at Customer onboarding; backup-withholding posture.

### C. Product

1. **MVP billing models offered to Customers.** Recommend: fixed-price subscriptions + usage-based metered components (the metering pipeline is core anyway); one-time purchases if cheap; defer per-seat/tiers/coupons as fast-follow. DECIDED: the platform **free tier is modeled as a $0 subscription** — every org has a subscription from day one, so entering monetization is a plan change, not a new enrollment, and the metering/entitlement machinery is exercised uniformly for free and paid orgs alike. DECIDED (direction): launch with **generic subscription templates** — a small fixed set of flat recurring price shapes an app owner picks from — and let real usage data drive the richer catalog (tiers, per-seat, metered) later. This costs the ledger nothing: Stripe Billing collapses every subscription shape into invoices → charges, and the ledger's only entry point is `recordCharge`, so subscription sophistication and ledger complexity grow on independent axes (ledger complexity comes from refunds/disputes/payouts, not billing models).
2. **Checkout & End-user portal.** Recommend Stripe Checkout (hosted, Kinotic-branded) + Stripe Customer Portal for self-serve subscription management at MVP; embedded Elements later.
3. **Monetization review checklist.** Exact contents of the gate (§9): category/MCC attestation, tax code, pricing linkage, prohibited list, refund-policy acknowledgment.
4. **Customer earnings UI scope.** Balance + statements + payout history from the ledger (ours), bank/account management via Connect embedded components — confirm this split.
5. **Statement descriptor scheme.** `KINOTIC* <APPNAME>` — prefix registration, app-name truncation rules (descriptor length limits), and the support-URL/receipt footer story.
6. **Developer sandbox story.** How app developers exercise checkout/billing pre-review (Stripe test mode per app? platform-provided test harness?).

### D. Engineering (recommendations included; confirm at review)

1. **Confirm SCT as the sole MVP rail** (§3). Destination charges deliberately not used — one split mechanism, batched transfers, ledger authoritative.
2. **Metadata contract** — finalize field set (`orgId`, `applicationId`, `tenantId`, `pricingPlanId`?) and versioning; it's immutable once live.
3. **Metric event schema** for Application runtimes (§8.2) — own design pass, first.
4. **Kinotic-native plan/catalog model** — how Customers define plans in Kinotic and how those project into Stripe Products/Prices on the platform account (naming, dedupe, price immutability handling).
5. **Graduation-path provisions** (§4.3) — confirm the rail-strategy interface and `merchantOfRecord` enum land in MVP even though only one rail ships.
6. **End-user identity mapping** — one Stripe `Customer` per End-user per Application (or per Org?); interaction with tenant partitioning.
7. **Billing Meters vs Metronome for usage-based billing.** Stripe's implementation planner now routes usage-based billing to **Metronome** (acquired by Stripe) rather than the Billing Meters API this design assumes. Evaluate: Metronome's fit/pricing for a Connect marketplace where subscriptions run on the platform account, vs the simpler Billing Meters path. Our own time-series store remains the source of truth either way, so this only changes the billing projection target (§8.2).

## 12. Platform setup checklist (admin/paperwork)

1. Create the Kinotic Stripe platform account; enable Connect; accept the Connect Platform Agreement.
2. Platform profile: legal entity, support email/phone, website, refund policy URL, branding, **statement descriptor prefix (`KINOTIC`)**.
3. Loss-liability posture declared to Stripe (`losses_collector: application`); Stripe reviews platform risk at Connect enablement — expect underwriting questions about the recipient/SCT model. **Hard prerequisite:** loss liability must be accepted in the Dashboard platform profile (`Settings → Connect → Platform profile`) **before** any connected account with platform-owned losses can be created.
4. **Connect must be enabled per environment** — sandboxes have their own account IDs and their own Connect/Accounts-v2 enablement (spike run 1 finding: `non_connect_platform_accounts_v2_access_blocked` in the sandbox until its platform setup is completed).
4. Enable **Radar for Platforms**; enable **Stripe Tax** on the platform account (registrations per §11-B4); enroll in the **Stripe 1099** product.
5. Register EventDestinations (two endpoints, thin payloads); store signing secrets per environment.
6. Working float on the platform balance (refunds and dispute debits draw on it before clawbacks land).
7. Build in test mode end-to-end first (Connect has a full sandbox; Stripe CLI `listen --forward-connect-to` for local dev).

## 13. Implementation order (proposed)

1. **Spike:** platform account + one recipient account end-to-end in test mode (v2 `recipient` configuration, hosted onboarding, embedded components, one SCT charge → transfer). DONE — validated recipient onboarding, transfer gating, fee timing, and the +24 h delay; the spike project was removed after completion (findings live in §6 and git history).
2. **Webhook skeleton:** `StripeWebhookEventService`, verifier verticle, dispatcher, DLQ — the idempotency machinery is the costliest thing to retrofit.
3. **Ledger core (§7):** entities, `RevenueSplitService.recordCharge`, derived balances — provable with test-mode charges before any UI exists.
4. **Org integration:** `OrganizationBillingProfile`, onboarding UI, monetization review gate wiring.
5. **Payouts:** scheduler, batched transfers, reconciliation, earnings statements.
6. **End-user billing:** plan/catalog projection, Kinotic-branded Checkout, subscriptions + metered components (metering aggregator → platform meters). Detailed design in §14; launch scope is generic templates only (metered components deferred).
7. **Dashboards:** usage + earnings views from the time-series store and ledger; refund/dispute ops tooling.

## 14. Two-level subscriptions (detail for §13.4–§13.6)

Two distinct subscription concepts, deliberately decoupled:

| Level | Who subscribes to whom | Selected by | MVP shape |
|---|---|---|---|
| **Platform plan** | Org → Kinotic, **per Application** | Org admin picks the Kinotic tier each app runs on | `FREE` only — internal state, no Stripe object (nothing to charge) |
| **App subscription** | End-user → Application (Kinotic as MoR) | Org admin defines plan templates for their app's users | Generic templates: flat monthly (annual optional) at an admin-chosen price |

The two compose freely — the canonical launch scenario is exactly: *org on the free platform tier while their application's users pay $X/month*. The platform tier governs resource limits and (later) the take rate; the app subscription governs end-user revenue. Money plumbing (billing profile, recipient account, payouts) stays **org-level** regardless of how many apps monetize.

### 14.1 Domain model additions (codebase)

| Entity | Key fields | Notes |
|---|---|---|
| `ApplicationPlan` (org-scoped) | `id`, `organizationId`, `applicationId`, `name`, `long amountMonthly` (minor units, `LEDGER_CURRENCY`), `PlanInterval interval` (`MONTHLY`, `ANNUAL`), `stripeProductId`, `stripePriceId`, `ApplicationPlanStatus status` (`DRAFT`, `ACTIVE`, `RETIRED`), `created`, `updated` | Kinotic-native source of truth, **projected** into Stripe (one Product per Application, one recurring Price per plan) on activation — never bound to a Stripe account (§4.3 rail escape hatch). Stripe Prices are immutable: a price change retires the plan and creates a successor |
| `AppSubscription` (org-scoped) | `id` = Stripe subscription id, `organizationId`, `applicationId`, `tenantId`, `endUserId`, `applicationPlanId`, `AppSubscriptionStatus status`, `currentPeriodEnd` | **Entitlement projection** maintained from `customer.subscription.*` webhooks — the application runtime's fast "is this user subscribed" check, no Stripe call in the request path |
| Platform plan (per app) | `platformPlanId` field on the Application (or its app-scoped config) | `FREE` constant at launch. When paid tiers arrive, `PlatformFeePolicy` widens from `feeBasisPointsFor(profile)` to resolve the **application's** plan — `RevenueSplit` already records `applicationId` + `pricingPlanId` per charge, so retroactive analysis works from day one |

Services (following §7.3 conventions): `ApplicationPlanService` (org-scoped CRUD; `activate(planId)` gated on the app having **passed monetization review** (§9) and projecting Product/Price into Stripe), `AppSubscriptionService` (webhook-driven projection plus `entitlementFor(applicationId, endUserId)`), and a checkout service minting Stripe Checkout Sessions (`mode=subscription`, the metadata contract stamped on the subscription — Stripe propagates it to invoices and charges, which is what makes `recordCharge` attribution work unchanged) and Customer Portal sessions.

Webhook dispatcher additions: `customer.subscription.created/updated/deleted` → `AppSubscriptionService`; `invoice.payment_failed` → subscription status `PAST_DUE` (Stripe Smart Retries handle recovery; §11-C's decisions). **No ledger changes** — renewals arrive as `charge.succeeded` like every other charge.

### 14.2 UI elements

**Org-admin (Kinotic portal):**
- *App Settings → Monetization tab*: platform tier display (Free); the guided monetization journey — recipient onboarding via embedded component when the org has no billing profile yet → plan editor (name + price) → activate (blocked until app review passes + recipient transfers are active) → earnings panel (`balanceFor`) with drill-down to splits.
- *Org → Billing page*: billing profile status, payout schedule (read-only until §13.5), statements later.

**End-user (inside the application):** a subscribe action that redirects to Kinotic-branded hosted Checkout and a manage action opening the Customer Portal — surfaced to app code through a platform SDK/endpoint that mints the sessions server-side (apps never see Stripe keys). The exact SDK surface is designed with the app-runtime team in §13.6 implementation.

### 14.3 Rollout order within §13.6

1. `ApplicationPlan` CRUD + Stripe projection (behind the monetization review gate)
2. Checkout session minting + metadata contract on subscriptions
3. `AppSubscription` projection + entitlement lookup
4. Portal UI (Monetization tab, earnings panel)
5. Publish the org-admin guide (Appendix B) to `website/content`

## 15. References

- Merchant of record in Connect — https://docs.stripe.com/connect/merchant-of-record
- Separate charges and transfers — https://docs.stripe.com/connect/separate-charges-and-transfers
- Service agreement types (recipient accounts) — https://docs.stripe.com/connect/service-agreement-types
- Accounts v2 — https://docs.stripe.com/connect/accounts-v2
- Connect embedded components — https://docs.stripe.com/connect/get-started-connect-embedded-components
- Risk management (losses, reserves, negative balances) — https://docs.stripe.com/connect/risk-management
- Disputes on Connect — https://docs.stripe.com/connect/disputes • Radar for Platforms — https://docs.stripe.com/radar/radar-for-platforms
- Payout schedules / balance settings — https://docs.stripe.com/connect/manage-payout-schedule
- Tax for platforms — https://docs.stripe.com/tax/tax-for-platforms
- Connect tax reporting (1099) — https://docs.stripe.com/connect/tax-reporting
- Connect pricing — https://stripe.com/connect/pricing
- Usage-based billing / Billing Meters — https://docs.stripe.com/billing/subscriptions/usage-based • https://docs.stripe.com/billing/subscriptions/usage-based/recording-usage-api
- Webhooks — https://docs.stripe.com/webhooks • https://docs.stripe.com/connect/webhooks • EventDestinations — https://docs.stripe.com/api/v2/event-destinations
- Legal: Platform Agreement — https://stripe.com/connect-account/legal/full • Recipient Agreement — https://stripe.com/connect-account/legal/recipient

## Appendix A. Alternatives considered (record)

- **Customer as MoR (direct charges + full merchant accounts, `application_fee_*` cut).** The prior draft's architecture. Rejected for MVP: weaker platform legitimacy, full merchant KYC friction on every Customer, and the "pay the platform bill from proceeds" goal requires two mechanisms + reconciliation instead of ledger netting. Preserved as the **graduation path** for large orgs (§4.3).
- **Destination charges as the primary rail.** Kinotic-MoR but Stripe auto-splits per charge. Rejected: forfeits transfer batching (per-payout fees), can't bundle across Applications, and the ledger is needed regardless — two split mechanisms is one too many.
- **Stripe Managed Payments (Stripe as MoR).** Explicitly incompatible with Connect (single sellers of their own digital products only). Useful only as the 7–9% pricing benchmark for MoR burden.

## Appendix B. Org-admin guide draft — "Add subscriptions to your application"

> DRAFT: moves to `website/content` when §13.6 ships (per the docs-reflect-current-system rule, it must not be published before the feature exists). Keep in sync with §14 while drafting.

Kinotic handles payments end to end for your application: your users subscribe through Kinotic checkout, Kinotic collects the money as the merchant of record, and you receive your share as automatic payouts — no payment processor account, tax registration, or PCI paperwork on your side. Your organization can stay on the free platform tier while your application's users pay for subscriptions; the two are independent.

### Prerequisites

- Your application is deployed and has passed monetization review (requested from App Settings → Monetization).
- You are an organization admin.

### 1. Connect your payout account

App Settings → Monetization → **Set up payouts**. You'll be asked for your business name, website, and a bank account — typically a few minutes. Payouts are held until this completes; your app can start selling as soon as transfers are enabled.

### 2. Create a plan

**Add plan** → give it a name your users will recognize (e.g. "Pro") and a monthly price. Annual billing is optional. Prices are fixed once a plan is live — to change pricing, retire the plan and create a new one; existing subscribers keep their price until they cancel.

### 3. Activate

**Activate** publishes the plan. From this moment your application can offer subscriptions.

### 4. Add subscribe to your application

Use the platform SDK to send a user to checkout and to check a user's subscription:

```
// exact SDK surface published with the feature — illustrative shape:
kinotic.billing.checkout(planId)        // redirects the current user to Kinotic checkout
kinotic.billing.entitlement()           // → { subscribed: true, plan: "Pro", renewsAt: ... }
kinotic.billing.managePortal()          // lets the user cancel or update their card
```

Your users see Kinotic-branded checkout and "KINOTIC* <YOUR APP>" on their card statement; Kinotic handles receipts, failed-payment retries, and refund requests under the platform refund policy.

### 5. Track your earnings

App Settings → Monetization shows your balance and every payment behind it: for each charge, the full breakdown — what the user paid, card processing cost, Kinotic's platform fee, and your net. Payouts arrive on your payout schedule (weekly by default) once your balance clears the hold period.

### Fees

Kinotic's platform fee is deducted from each payment along with card processing costs; the remainder is yours. Current rates are shown on the Monetization tab before you activate a plan.