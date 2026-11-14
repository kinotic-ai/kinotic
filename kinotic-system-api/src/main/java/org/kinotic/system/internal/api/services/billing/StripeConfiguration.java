package org.kinotic.system.internal.api.services.billing;

import com.stripe.StripeClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.config.BillingProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The client of Minds Ignited's Stripe account that organizations are billed through, where billing is on.
 */
@Configuration
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
public class StripeConfiguration {

    @Bean
    public StripeClient stripeClient(KinoticManagementApiProperties properties) {
        BillingProperties billing = properties.getManagementApi().getBilling();
        Validate.notBlank(billing.getStripeSecretKey(),
                          "kinotic.managementApi.billing.stripeSecretKey is required while billing is enabled");
        Validate.notBlank(billing.getStripeWebhookSecret(),
                          "kinotic.managementApi.billing.stripeWebhookSecret is required while billing is enabled");
        return new StripeClient(billing.getStripeSecretKey());
    }
}
