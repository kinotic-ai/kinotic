package org.kinotic.testserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain_autoconfig.KinoticDomainAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A gateway for the {@code @kinotic-ai/core} RPC tests: kinotic-core and the api gateway, with
 * {@link TestSecurityService} authenticating connections and {@link ITestService} to call.
 * It needs no Elasticsearch.
 */
// Only kinotic-domain's model types are used here; its services need Elasticsearch
@SpringBootApplication(exclude = KinoticDomainAutoConfiguration.class)
@EnableKinotic
public class TestServerApplication {

	static void main(String[] args) {
		SpringApplication.run(TestServerApplication.class, args);
	}

}
