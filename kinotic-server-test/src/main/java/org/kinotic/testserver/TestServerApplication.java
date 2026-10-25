package org.kinotic.testserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain.api.services.security.ScopedParticipantZoneRulesResolver;
import org.kinotic.domain_autoconfig.KinoticDomainAutoConfiguration;
import org.kinotic.sql_autoconfig.KinoticSqlAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * A gateway for the {@code @kinotic-ai/core} RPC tests: kinotic-core and the api gateway, with
 * {@link TestSecurityService} authenticating connections and {@link ITestService} to call.
 * It needs no Elasticsearch.
 */
// Only kinotic-domain's participant types and their zone rules are used here; its services, and the kinotic-sql
// executors it carries, need Elasticsearch
@SpringBootApplication(exclude = {KinoticDomainAutoConfiguration.class, KinoticSqlAutoConfiguration.class})
@Import(ScopedParticipantZoneRulesResolver.class)
@EnableKinotic
public class TestServerApplication {

	static void main(String[] args) {
		SpringApplication.run(TestServerApplication.class, args);
	}

}
