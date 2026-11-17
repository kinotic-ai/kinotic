package org.kinotic.management.api.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Billing of organizations for their use of Kinotic Cloud, through Minds Ignited's Stripe account. Off unless
 * {@link #enabled}: a self-hosted installation never turns it on, and then no billing service is published, no
 * Stripe webhook is served, and no organization is ever billed or deactivated.
 * <p>
 * In Kubernetes the secret values ({@link #stripeSecretKey}, {@link #stripeWebhookSecret}) are files of the system
 * server's system-secrets volume, each named after its property (e.g. {@code kinotic.managementApi.billing.stripeSecretKey}).
 * <p>
 * Bound under {@code kinotic.managementApi.billing.*} via {@link KinoticManagementApiProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingProperties {

    /**
     * Whether organizations are billed. When true, {@link #stripeSecretKey} and {@link #stripeWebhookSecret} are
     * required.
     */
    private boolean enabled = false;

    /**
     * How many days the free trial of a new organization lasts, counted from the organization's creation. A trial
     * needs no payment details.
     */
    @Min(1)
    private int trialDays = 30;

    /**
     * The Stripe secret API key ({@code sk_test_...} or {@code sk_live_...}), or a restricted key with write access
     * to Products, Prices, Billing Meters, Customers, Checkout Sessions, Customer Portal Sessions and Subscriptions.
     */
    private String stripeSecretKey;

    /**
     * The signing secret ({@code whsec_...}) of the Stripe webhook endpoint that delivers to the system server's
     * {@code POST /api/billing/stripe/webhook}.
     */
    private String stripeWebhookSecret;
}
