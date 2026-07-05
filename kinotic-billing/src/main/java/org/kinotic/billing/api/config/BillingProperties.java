package org.kinotic.billing.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Stripe platform-account configuration for the billing module.
 * <p>
 * In production the secret values are mounted by the AKS Secret Store CSI driver as
 * environment variables and bound via Spring's relaxed property binding (e.g.
 * {@code KINOTIC_BILLING_STRIPESECRETKEY}). For local dev they can also be set via
 * {@code application.yml} or any other Spring-supported source.
 * <p>
 * Bound under {@code kinotic.billing.*} via {@link KinoticBillingProperties}; required
 * fields are validated at boot via Jakarta Bean Validation (only when the module is
 * enabled — see {@code KinoticBillingLibrary}).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingProperties {

    /**
     * Master switch for the billing module. When {@code false} (the default) no billing
     * beans are created and the server behaves as if the module were absent.
     */
    private boolean enabled = false;

    /**
     * Secret API key for Kinotic's Stripe platform account ({@code sk_test_...} /
     * {@code sk_live_...}). The key's mode also determines which {@code livemode}
     * webhook events are processed.
     */
    @NotBlank
    private String stripeSecretKey;

    /**
     * Signing secret ({@code whsec_...}) for the platform-stream webhook endpoint
     * ({@code /webhooks/stripe/platform}).
     */
    @NotBlank
    private String platformWebhookSigningSecret;

    /**
     * Signing secret ({@code whsec_...}) for the connected-accounts-stream webhook
     * endpoint ({@code /webhooks/stripe/connected}). Separate from the platform secret
     * so a leak of one doesn't compromise the other.
     */
    @NotBlank
    private String connectedWebhookSigningSecret;

}
