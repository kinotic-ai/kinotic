

package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

@SpringBootTest
public class ApplicationTests extends KinoticTestBase {

	@Autowired
	private ApplicationService applicationService;

	@Test
	public void createAndDeleteApplication() {
		Application test = new Application();
		test.setId("Test");
		test.setDescription("Testing This Application");

		CompletableFuture<Application> future = applicationService.save(test);

		StepVerifier.create(Mono.fromFuture(future))
					.expectNextMatches(application -> application.getId().equals("Test") && application.getUpdated() != null)
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(applicationService.deleteById(test.getId())))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(applicationService.findById(test.getId())))
					.expectComplete()
					.verify();
	}

}
