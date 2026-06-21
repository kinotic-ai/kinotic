package org.kinotic.domain_autoconfig;

import org.kinotic.domain.KinoticDomainLibrary;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

// KinoticDomainProperties is enabled here (always-on) rather than via KinoticDomainLibrary's
// @ComponentScan, which is gated by kinotic.disableDomain. The api-gateway reads domain.ssl
// even when domain logic is disabled, so the properties must remain available.
@AutoConfiguration
@Import(KinoticDomainLibrary.class)
@EnableConfigurationProperties(KinoticDomainProperties.class)
public class KinoticDomainAutoConfiguration {}
