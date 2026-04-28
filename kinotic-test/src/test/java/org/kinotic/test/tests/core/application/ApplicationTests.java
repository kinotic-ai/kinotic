

package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Test;
import org.kinotic.os.api.model.Application;
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
		Application test = new Application();
		test.setId("Test");
		test.setOrganizationId(TEST_ORG_ID);
		test.setDescription("Testing This Application");

		StepVerifier.create(Mono.fromFuture(elevated(() -> applicationService.save(test))))
					.expectNextMatches(application -> application.getId().equals("Test") && application.getUpdated() != null)
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> applicationService.deleteById(test.getId()))))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> applicationService.findById(test.getId()))))
					.expectComplete()
					.verify();
	}

}
