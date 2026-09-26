package org.kinotic.clienttest;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain_autoconfig.KinoticDomainAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A gateway for the {@code @kinotic-ai/core} RPC tests: kinotic-core and the api gateway, with
 * {@link TestSecurityService} authenticating connections and {@link ITestService} to call.
 * It needs no Elasticsearch.
 */
// Only kinotic-domain's model types are used here; its services need Elasticsearch and would
// bring the real SecurityService in place of TestSecurityService
@SpringBootApplication(exclude = KinoticDomainAutoConfiguration.class)
@EnableKinotic
public class ClientTestServerApplication {

	static void main(String[] args) {
		SpringApplication.run(ClientTestServerApplication.class, args);
	}

}
