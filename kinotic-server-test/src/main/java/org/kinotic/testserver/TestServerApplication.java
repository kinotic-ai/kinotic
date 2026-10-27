package org.kinotic.testserver;

import org.kinotic.authz_autoconfig.KinoticAuthzAutoConfiguration;
import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain_autoconfig.KinoticDomainAutoConfiguration;
import org.kinotic.sql_autoconfig.KinoticSqlAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A gateway for the {@code @kinotic-ai/core} RPC tests: kinotic-core and the api gateway, with
 * {@link TestSecurityService} authenticating connections, {@link TestZoneRulesResolver} deciding their zones,
 * {@link TestRequestAuthorizer} admitting every request and {@link ITestService} to call. It needs no
 * Elasticsearch and no authorization engine.
 */
// Only kinotic-domain's participant types are used here; its services, and the kinotic-sql executors it
// carries, need Elasticsearch, and the kinotic-authz services it carries need OpenFGA
@SpringBootApplication(exclude = {KinoticDomainAutoConfiguration.class,
                                  KinoticSqlAutoConfiguration.class,
                                  KinoticAuthzAutoConfiguration.class})
@EnableKinotic
public class TestServerApplication {

	static void main(String[] args) {
		SpringApplication.run(TestServerApplication.class, args);
	}

}
