package org.kinotic.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Root configuration for the Kinotic billing module (Stripe merchant-of-record billing:
 * webhook ingest, revenue-share ledger, payouts).
 * <p>
 * Disabled unless {@code kinotic.billing.enabled=true} so the module can ship in the server
 * without requiring Stripe secrets in environments that don't bill yet. When enabled, the
 * required secrets are validated at boot.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.billing.enabled", havingValue = "true")
public class KinoticBillingLibrary {
}
