

package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
public class ApplicationTests extends KinoticTestBase {

	@Autowired
	private ApplicationService applicationService;

	@Test
	public void createAndDeleteApplication() {
		Application test = new Application("Test App", "Testing This Application");
		test.setOrganizationId(TEST_ORG_ID);

		StepVerifier.create(Mono.fromFuture(runAsOrganization(() -> applicationService.save(test))))
					.expectNextMatches(application -> application.getId().equals("test_app") && application.getUpdated() != null)
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(runAsOrganization(() -> applicationService.deleteById(test.getId()))))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(runAsOrganization(() -> applicationService.findById(test.getId()))))
					.expectComplete()
					.verify();
	}

	@Test
	public void createDerivesTheIdFromTheSlugifiedName() {
		Application test = new Application("Slugs. And Snails!", "Testing id derivation");
		test.setOrganizationId(TEST_ORG_ID);

		StepVerifier.create(Mono.fromFuture(runAsOrganization(() -> applicationService.create(test))))
					.expectNextMatches(application -> application.getId().equals("slugs_and_snails")
							&& application.getName().equals("Slugs. And Snails!"))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(runAsOrganization(() -> applicationService.deleteById("slugs_and_snails"))))
					.expectComplete()
					.verify();
	}

}
