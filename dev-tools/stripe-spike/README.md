# Stripe MoR Spike

Test-mode probe of the Kinotic-as-MoR billing design ([docs/stripe-connect-billing.md](../../docs/stripe-connect-billing.md) §13.1). Not part of the main build.

## Prerequisites

1. A Stripe account with **Connect enabled** (Dashboard → Connect → Get started; choose the "platform collects fees / is liable" posture).
2. A **test-mode** secret key (`sk_test_...`). The spike refuses non-test keys.

## Run

```bash
STRIPE_SECRET_KEY=sk_test_... ./gradlew -p dev-tools/stripe-spike run
```

Step 2 prints a hosted-onboarding URL. Open it, complete it with Stripe test data (any name, test routing `110000000` / account `000123456789`), then re-run reusing the account so step 6 (transfer) can succeed:

```bash
STRIPE_SECRET_KEY=sk_test_... STRIPE_SPIKE_ACCOUNT_ID=acct_... ./gradlew -p dev-tools/stripe-spike run
```

## What each step probes

| Step | Probe | Design assumption at risk |
|---|---|---|
| 1 | v2 `/v2/core/accounts` accepts the recipient-configuration payload from §6 | Exact JSON shape, capability path, `include` behavior, whether Connect must be pre-enabled |
| 2 | v1 `account_links` works against a v2-created account | Hosted onboarding path for recipient accounts |
| 3 | `requirements` on the recipient account | "Light KYC" claim — what is actually required |
| 4 | Platform-account charge with `orgId`/`applicationId`/`tenantId` metadata | The SCT rail + metadata attribution contract |
| 5 | Balance-transaction fee decomposition | RevenueSplit math inputs (exact Stripe fee, settle lag) |
| 6 | Transfer with `source_transaction` + `transfer_group` + idempotency key | Transfers gated on onboarding; error text pre-onboarding |
| 7 | Platform + connected balances (`Stripe-Account` header) | Recipient balance visibility, +24 h availability |

## Findings log

Record what actually happened (this section is the spike's deliverable):

Run 1 (2026-07-04, platform `acct_1TpbeTH8D77dbtsk`, pre-activation, `charges_enabled=false`):

- [x] Step 1 — **BLOCKED: Connect platform setup must be completed first.** `HTTP 400 non_connect_platform_accounts_v2_access_blocked`: "Accounts v2 is not enabled for your sandbox merchant". Fix: complete https://dashboard.stripe.com/acct_1TpbeTH8D77dbtsk/settings/connect/platform-setup, then rerun. Confirms design assumption: Connect enablement (and its platform questionnaire) is a hard prerequisite for the v2 recipient-account API.
- [ ] Step 2 — skipped (no account created).
- [ ] Step 3 — skipped (no account created).
- [x] Step 4 — **Platform charge works** even pre-activation in test mode: `pi_3TpfsEH8D77dbtsk...` → `succeeded`, charge created, metadata contract accepted.
- [x] Step 5 — **`balance_transaction` was NOT present immediately after a synchronous confirm**, even for a test card. Validates the design rule that `RevenueSplit.recordCharge` must be webhook-driven (`charge.succeeded` / `charge.updated`) and tolerate settle lag — never read the Stripe fee synchronously after payment.
- [ ] Step 6 — skipped (no account created).
- [x] Step 7 — Balance reads work; available/pending both $0 at read time (consistent with the step 5 lag — the $50 charge hadn't produced its balance transaction yet).

Next: complete Connect platform setup in the dashboard → rerun → complete onboarding link → rerun with `STRIPE_SPIKE_ACCOUNT_ID` to exercise steps 2, 3, 6.

Run 2 (2026-07-04, after Connect platform setup; created `acct_1TpgpnH8D7GDkp1N`):

- [x] Step 1 — **v2 recipient account created with the design §6 payload verbatim.** `applied_configurations: ["recipient"]`, both `stripe_balance` capabilities start `restricted` / `requirements_past_due`.
- [x] Step 2 — **v1 `account_links` works against a v2-created account** (`connect.stripe.com/setup/c/...`). Hosted onboarding is a viable bridge until embedded onboarding is integrated.
- [x] Step 3 — **The recipient KYC surface (US company), quantified.** Initial requirements: `identity.business_details.registered_name`, `defaults.profile.business_url`, ToS acceptance (`date` + `ip`), `external_account` (bank). Notably: **no representative SSN/DOB, no beneficial owners** in the initial set — the light-onboarding claim holds. Two-tier gating discovered: `external_account` and `us_ein` (which is only `eventually_due`) restrict **payouts only** — `stripe_transfers` needs just name + URL + ToS. So a recipient can receive transfers into their Stripe balance before adding a bank account or EIN.
- [x] Step 4 — Charge + metadata contract OK again.
- [x] Step 5 — `balance_transaction` again absent immediately after synchronous confirm (2/2 runs) — webhook-driven fee resolution is mandatory, not defensive.
- [x] Step 6 — Transfer correctly rejected pre-onboarding: `insufficient_capabilities_for_transfer` ("destination account needs ... configurations.recipient.capabilities.stripe_balance.stripe_transfers"). Confirms the exact capability to gate on and the error code to handle.
- [x] Step 7 — Platform pending balance = 9650 = exactly 2 × (5000 − 175) — the two spike charges net of 2.9% + 30¢. Recipient balance readable via `Stripe-Account` header, $0 as expected.

Next: open the step 2 onboarding URL, complete with test data (routing `110000000`, account `000123456789`), then rerun with `STRIPE_SPIKE_ACCOUNT_ID=acct_1TpgpnH8D7GDkp1N` to prove the transfer leg and recipient balance credit.

- [x] Additional finding — **account links are single-use and expire within minutes.** An expired/used link redirects to the `refresh_url` (stubbed to example.com in the spike). Production must implement `refresh_url` as an endpoint that mints a fresh account link server-side and redirects — or use embedded onboarding, which has no link-expiry problem at all. To get a fresh link: rerun with `STRIPE_SPIKE_ACCOUNT_ID` set and open the step 2 URL immediately.

Run 3 (2026-07-04, after completing hosted onboarding) — **SPIKE COMPLETE, full money loop proven**:

- [x] Step 3 — All past-due requirements cleared by onboarding; only `us_ein` remains, `eventually_due`, restricting `payouts` only. Production implication: EIN must *eventually* be collected or payouts stop — exactly the notification-banner / handle-verification-updates use case.
- [x] Step 5 — `balance_transaction` absent immediately after confirm, 3/3 runs. Settled: fee resolution is webhook-driven, full stop.
- [x] Step 6 — **Transfer succeeded**: `tr_3Tph83H8D77dbtsk0K77YQ16`, amount 4575. Notable: it succeeded with `source_transaction` pointing at a charge whose balance transaction didn't exist yet — confirming documented behavior that source-transaction transfers accept immediately and execute when the charge's funds land.
- [x] Step 7 — **Recipient pending balance = 4575** — the exact transfer amount, in `pending` not `available`, confirming the +24 h recipient availability delay. Platform balance summary during in-flight source-transaction transfers does not decompose trivially (charge pending + transfer debit interleave) — reconciliation lesson: account from **balance transactions**, never from the balance summary. The ledger design already does this.

**End-to-end proven:** End-user charge on platform account (with metadata contract) → platform balance → capability-gated transfer → recipient balance. Every Stripe-side assumption in docs/stripe-connect-billing.md that is verifiable in test mode has been verified. Next engineering step per §13: the webhook skeleton.

Known open SDK question: the v2 Accounts surface is beta-gated in `stripe-java` (spike uses raw HTTP for v2 on purpose). Note here which `stripe-java` version, if any, exposes `v2.core.accounts` for the real `StripeClient` facade.
