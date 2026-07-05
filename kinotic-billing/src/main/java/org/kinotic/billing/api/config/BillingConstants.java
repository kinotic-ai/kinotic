package org.kinotic.billing.api.config;

/**
 * Constants shared across the billing module. These are the same in every environment —
 * per project convention they are constants, not properties.
 */
public final class BillingConstants {

    /**
     * Webhook paths live outside {@code /api/*} deliberately: the gateway installs a global
     * 16 KiB {@link io.vertx.ext.web.handler.BodyHandler} and a session handler on
     * {@code /api/*}, neither of which should apply to Stripe deliveries (webhooks carry no
     * session, and events can exceed 16 KiB). The webhook handler adds its own route-scoped
     * body handler instead.
     */
    public static final String PLATFORM_WEBHOOK_PATH = "/webhooks/stripe/platform";
    public static final String CONNECTED_WEBHOOK_PATH = "/webhooks/stripe/connected";

    /** Local event-bus address the webhook handler publishes received event ids to. */
    public static final String STRIPE_WEBHOOK_EVENT_ADDRESS = "kinotic.billing.stripe-webhook-event";

    /** Stripe event payloads are small (thin events ~2 KiB); 1 MiB is a generous ceiling. */
    public static final long WEBHOOK_BODY_LIMIT_BYTES = 1024 * 1024;

    /**
     * Platform take rate applied until the plan-catalog round introduces per-plan pricing.
     * 500 basis points = 5%, the economic floor from the billing design doc.
     */
    public static final long DEFAULT_PLATFORM_FEE_BASIS_POINTS = 500;

    // Metadata contract: every Stripe object Kinotic creates carries these keys, and the
    // ledger resolves ownership from them. Treat as an immutable API.
    public static final String METADATA_ORG_ID = "orgId";
    public static final String METADATA_APPLICATION_ID = "applicationId";
    public static final String METADATA_TENANT_ID = "tenantId";

    private BillingConstants() {
    }
}
