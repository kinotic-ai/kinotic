package org.kinotic.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Test-mode spike for the Kinotic-as-MoR billing design (docs/stripe-connect-billing.md §13.1).
 *
 * Probes, in order:
 *   1. v2 Accounts API: create a recipient-configuration connected account (raw HTTP, pinned Stripe-Version)
 *   2. Hosted onboarding link for that account (v1 account_links against a v2-created account)
 *   3. SCT rail: platform-account PaymentIntent with the attribution metadata contract
 *   4. Balance transaction fee decomposition (the RevenueSplit math inputs)
 *   5. Transfer to the recipient account (source_transaction + transfer_group + idempotency key)
 *   6. Platform + connected balances
 *
 * Every Stripe error is a spike FINDING, not a failure — steps print the error and continue where safe.
 *
 * Run:  STRIPE_SECRET_KEY=sk_test_... ./gradlew -p dev-tools/stripe-spike run
 * Rerun against an existing account (e.g. after completing onboarding in the browser):
 *       STRIPE_SECRET_KEY=sk_test_... STRIPE_SPIKE_ACCOUNT_ID=acct_... ./gradlew -p dev-tools/stripe-spike run
 */
public class StripeSpike {

    // Pinned per design §6 — v2 responses change shape across versions
    private static final String STRIPE_VERSION = "2026-01-28.clover";
    private static final String API_BASE = "https://api.stripe.com";
    private static final long CHARGE_AMOUNT = 5000L;          // $50.00
    private static final long PLATFORM_FEE_PERCENT = 5L;      // sample take rate for split math

    private final String apiKey;
    private final StripeClient stripe;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private StripeSpike(String apiKey) {
        this.apiKey = apiKey;
        this.stripe = new StripeClient(apiKey);
    }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("STRIPE_SECRET_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("STRIPE_SECRET_KEY is not set. Get a test-mode secret key (sk_test_...) from the Stripe dashboard.");
            System.exit(1);
        }
        if (!key.startsWith("sk_test_") && !key.startsWith("rk_test_")) {
            System.err.println("Refusing to run: key does not look like a TEST key (sk_test_/rk_test_). This spike must never run in live mode.");
            System.exit(1);
        }
        new StripeSpike(key).run(System.getenv("STRIPE_SPIKE_ACCOUNT_ID"));
    }

    private void run(String existingAccountId) {
        banner("STEP 0: verify key / platform account");
        try {
            JsonNode acct = v1Get("/v1/account");
            System.out.println("Platform account: " + acct.path("id").asText()
                    + "  charges_enabled=" + acct.path("charges_enabled").asBoolean()
                    + "  country=" + acct.path("country").asText());
        } catch (Exception e) {
            System.err.println("FATAL: key rejected: " + e.getMessage());
            return;
        }

        String accountId = existingAccountId;
        if (accountId == null || accountId.isBlank()) {
            accountId = step1CreateRecipientAccountV2();
        } else {
            System.out.println("Reusing account from STRIPE_SPIKE_ACCOUNT_ID: " + accountId);
        }

        if (accountId != null) {
            step2OnboardingLink(accountId);
            step3AccountRequirements(accountId);
        }

        String chargeId = step4PlatformCharge();
        if (chargeId != null) {
            step5FeeDecomposition(chargeId);
            if (accountId != null) {
                step6Transfer(accountId, chargeId);
            }
        }
        step7Balances(accountId);

        banner("DONE — record findings in dev-tools/stripe-spike/README.md");
        if (accountId != null) {
            System.out.println("Re-run after completing onboarding with: STRIPE_SPIKE_ACCOUNT_ID=" + accountId);
        }
    }

    /** Probes the exact v2 payload shape from design §6. API error text here IS the finding. */
    private String step1CreateRecipientAccountV2() {
        banner("STEP 1: create v2 recipient-configuration account (raw HTTP)");
        String payload = """
                {
                  "display_name": "Spike Test Org",
                  "contact_email": "spike-test@example.com",
                  "dashboard": "none",
                  "identity": {
                    "country": "us",
                    "entity_type": "company"
                  },
                  "configuration": {
                    "recipient": {
                      "capabilities": {
                        "stripe_balance": {
                          "stripe_transfers": { "requested": true }
                        }
                      }
                    }
                  },
                  "defaults": {
                    "currency": "usd",
                    "responsibilities": {
                      "fees_collector": "application",
                      "losses_collector": "application"
                    }
                  },
                  "include": ["configuration.recipient", "identity", "requirements"]
                }
                """;
        try {
            JsonNode resp = v2Post("/v2/core/accounts", payload);
            String id = resp.path("id").asText(null);
            System.out.println("Created v2 account: " + id);
            System.out.println(resp.toPrettyString());
            return id;
        } catch (Exception e) {
            System.err.println("FINDING (v2 account create failed): " + e.getMessage());
            System.err.println("→ If this is a version/shape error, adjust the payload per the error text and note it in the README.");
            return null;
        }
    }

    /** v1 account_links against a v2-created account — whether this works is a finding. */
    private void step2OnboardingLink(String accountId) {
        banner("STEP 2: hosted onboarding link");
        try {
            var link = stripe.accountLinks().create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl("https://example.com/spike/refresh")
                    .setReturnUrl("https://example.com/spike/return")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            System.out.println("Open this in a browser to complete recipient onboarding (test data):");
            System.out.println("  " + link.getUrl());
        } catch (StripeException e) {
            System.err.println("FINDING (v1 account_links vs v2 account): " + e.getMessage());
            System.err.println("→ May need the v2 account-link endpoint instead; note the exact error.");
        }
    }

    /** What KYC does a recipient account actually require in test mode? */
    private void step3AccountRequirements(String accountId) {
        banner("STEP 3: account requirements (recipient KYC surface)");
        try {
            JsonNode resp = v2Get("/v2/core/accounts/" + accountId + "?include=requirements&include=configuration.recipient");
            System.out.println(resp.path("requirements").toPrettyString());
        } catch (Exception e) {
            System.err.println("FINDING (v2 account retrieve): " + e.getMessage());
        }
    }

    /** SCT rail: charge on the PLATFORM account carrying the attribution metadata contract (§7.1.4). */
    private String step4PlatformCharge() {
        banner("STEP 4: platform-account charge (SCT rail) with metadata contract");
        try {
            Customer endUser = stripe.customers().create(CustomerCreateParams.builder()
                    .setEmail("end-user@example.com")
                    .putMetadata("orgId", "spike-org")
                    .putMetadata("applicationId", "spike-app")
                    .putMetadata("tenantId", "spike-tenant")
                    .build());
            PaymentIntent pi = stripe.paymentIntents().create(PaymentIntentCreateParams.builder()
                    .setAmount(CHARGE_AMOUNT)
                    .setCurrency("usd")
                    .setCustomer(endUser.getId())
                    .setPaymentMethod("pm_card_visa")
                    .setConfirm(true)
                    .addPaymentMethodType("card")
                    .putMetadata("orgId", "spike-org")
                    .putMetadata("applicationId", "spike-app")
                    .putMetadata("tenantId", "spike-tenant")
                    .build());
            System.out.println("PaymentIntent: " + pi.getId() + "  status=" + pi.getStatus());
            String chargeId = pi.getLatestCharge();
            System.out.println("Charge: " + chargeId);
            return chargeId;
        } catch (StripeException e) {
            System.err.println("FINDING (platform charge): " + e.getMessage());
            return null;
        }
    }

    /** The exact inputs RevenueSplit needs: fee from the balance transaction, not estimated. */
    private void step5FeeDecomposition(String chargeId) {
        banner("STEP 5: balance-transaction fee decomposition (RevenueSplit math)");
        try {
            Charge charge = stripe.charges().retrieve(chargeId);
            String btId = charge.getBalanceTransaction();
            if (btId == null) {
                System.out.println("FINDING: balance_transaction not yet present on charge — RevenueSplit must tolerate settle lag.");
                return;
            }
            BalanceTransaction bt = stripe.balanceTransactions().retrieve(btId);
            long gross = bt.getAmount();
            long stripeFee = bt.getFee();
            long platformFee = gross * PLATFORM_FEE_PERCENT / 100;
            long netToOrg = gross - stripeFee - platformFee;
            System.out.println("gross=" + gross + "  stripeFee=" + stripeFee
                    + "  kinoticFee(" + PLATFORM_FEE_PERCENT + "%)=" + platformFee
                    + "  netToOrg=" + netToOrg);
            bt.getFeeDetails().forEach(fd ->
                    System.out.println("  fee detail: " + fd.getType() + " = " + fd.getAmount()));
        } catch (StripeException e) {
            System.err.println("FINDING (fee decomposition): " + e.getMessage());
        }
    }

    /** Expected to fail until onboarding completes — the exact error is the KYC-gate finding. */
    private void step6Transfer(String accountId, String chargeId) {
        banner("STEP 6: transfer to recipient account (expected to fail pre-onboarding)");
        try {
            long gross = CHARGE_AMOUNT;
            long netToOrg = gross - (gross * PLATFORM_FEE_PERCENT / 100) - 175; // rough fee est. only for the spike
            Transfer transfer = stripe.transfers().create(
                    TransferCreateParams.builder()
                            .setAmount(netToOrg)
                            .setCurrency("usd")
                            .setDestination(accountId)
                            .setSourceTransaction(chargeId)
                            .setTransferGroup("spike-payout-1")
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("spike-payout-1-" + UUID.randomUUID())
                            .build());
            System.out.println("Transfer created: " + transfer.getId() + "  amount=" + transfer.getAmount());
        } catch (StripeException e) {
            System.err.println("FINDING (transfer to recipient): " + e.getMessage());
            System.err.println("→ If capability-related: confirms transfers are gated on onboarding; rerun with STRIPE_SPIKE_ACCOUNT_ID after completing the link from step 2.");
        }
    }

    private void step7Balances(String accountId) {
        banner("STEP 7: balances");
        try {
            Balance platform = stripe.balance().retrieve();
            System.out.println("Platform available: " + platform.getAvailable() + "  pending: " + platform.getPending());
        } catch (StripeException e) {
            System.err.println("FINDING (platform balance): " + e.getMessage());
        }
        if (accountId != null) {
            try {
                Balance connected = stripe.balance().retrieve(
                        RequestOptions.builder().setStripeAccount(accountId).build());
                System.out.println("Recipient " + accountId + " available: " + connected.getAvailable()
                        + "  pending: " + connected.getPending());
            } catch (StripeException e) {
                System.err.println("FINDING (connected balance via Stripe-Account header): " + e.getMessage());
            }
        }
    }

    // ---- raw HTTP helpers (v2 endpoints are beta-gated in the Java SDK; raw keeps the spike version-proof) ----

    private JsonNode v2Post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Stripe-Version", STRIPE_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    private JsonNode v2Get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Stripe-Version", STRIPE_VERSION)
                .GET()
                .build();
        return send(req);
    }

    private JsonNode v1Get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + path))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return send(req);
    }

    private JsonNode send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode body = mapper.readTree(resp.body());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + body.toPrettyString());
        }
        return body;
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println(title);
        System.out.println("========================================================");
    }
}
