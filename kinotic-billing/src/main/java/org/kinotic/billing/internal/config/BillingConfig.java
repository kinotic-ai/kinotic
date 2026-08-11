package org.kinotic.billing.internal.config;

import com.stripe.StripeClient;
import org.kinotic.billing.api.config.KinoticBillingProperties;
import org.kinotic.billing.api.services.RevenueSplitService;
import org.kinotic.billing.internal.endpoints.StripeEventDispatcher;
import org.kinotic.billing.internal.endpoints.StripeWebhookSignatureVerifier;
import org.kinotic.billing.internal.endpoints.WebhookRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BillingConfig {

    @Bean
    public StripeClient stripeClient(KinoticBillingProperties properties) {
        return new StripeClient(properties.getBilling().getStripeSecretKey());
    }

    @Bean
    public StripeWebhookSignatureVerifier stripeWebhookSignatureVerifier() {
        return new StripeWebhookSignatureVerifier(Clock.systemUTC());
    }

    @Bean
    public WebhookRetryPolicy webhookRetryPolicy() {
        return new WebhookRetryPolicy();
    }

    @Bean
    public StripeEventDispatcher stripeEventDispatcher(RevenueSplitService revenueSplitService,
                                                       KinoticBillingProperties properties) {
        return new StripeEventDispatcher(revenueSplitService,
                                         isLiveKey(properties.getBilling().getStripeSecretKey()));
    }

    private static boolean isLiveKey(String stripeSecretKey) {
        return stripeSecretKey != null
                && (stripeSecretKey.startsWith("sk_live_") || stripeSecretKey.startsWith("rk_live_"));
    }
}
