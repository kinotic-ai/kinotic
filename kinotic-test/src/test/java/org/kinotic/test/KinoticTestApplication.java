package org.kinotic.test;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;


@SpringBootApplication
@ActiveProfiles("test")
@EnableConfigurationProperties
@EnableKinotic
public class KinoticTestApplication {
    static void main(String[] args) {
		SpringApplication.run(KinoticTestApplication.class, args);
	}

	// Every module runs in this one node, so it hosts every platform zone, which also places the
	// cluster singletons pinned to a zone's nodes here
	@Bean
	public ZonePartition zonePartition() {
		Set<String> zones = Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.SYSTEM_API_ZONE,
								   DomainUtil.APP_API_ZONE, DomainUtil.APP_ZONE_PREFIX);
		return ZonePartition.of("kinotic-test", zones, zones);
	}

}
