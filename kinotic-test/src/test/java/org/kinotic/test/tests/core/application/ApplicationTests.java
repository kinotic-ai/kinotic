

package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ApplicationTests extends KinoticTestBase {

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Test
	public void createAndDeleteApplication() {
		Application test = new Application("Test App", "Testing This Application");
		test.setOrganizationId(TEST_ORG_ID);

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> applicationService.save(test)).toCompletionStage()))
					.expectNextMatches(application -> application.getId().equals("test-app") && application.getUpdated() != null)
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> applicationService.deleteById(test.getId())).toCompletionStage()))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> applicationService.findById(test.getId())).toCompletionStage()))
					.expectComplete()
					.verify();
	}

	@Test
	public void createDerivesTheIdFromTheSlugifiedName() {
		Application test = new Application("Slugs. And Snails!", "Testing id derivation");
		test.setOrganizationId(TEST_ORG_ID);

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> applicationService.create(test)).toCompletionStage()))
					.expectNextMatches(application -> application.getId().equals("slugs-and-snails")
							&& application.getName().equals("Slugs. And Snails!"))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> applicationService.deleteById("slugs-and-snails")).toCompletionStage()))
					.expectComplete()
					.verify();
	}

	@Test
	public void theFirstClaimedUiBecomesThePrimaryUiAndALaterClaimLeavesIt() throws Exception {
		Application created = await(runAsOrganization(() -> applicationService.create(new Application("Primary Claim", "Testing the primary UI claim")
																							   .setOrganizationId(TEST_ORG_ID))));
		ApplicationKey key = new ApplicationKey(TEST_ORG_ID, created.getId());
		try {
			assertTrue(await(applicationRepository.claimPrimaryUi(key, "web", "https://primary-claim--web.test-org.example.com")));
			assertFalse(await(applicationRepository.claimPrimaryUi(key, "admin", "https://primary-claim--admin.test-org.example.com")));

			Application claimed = await(applicationRepository.findById(created.getId(), TEST_ORG_ID));
			assertEquals("web", claimed.getPrimaryUiId());
			assertEquals("https://primary-claim--web.test-org.example.com", claimed.getPrimaryUiUrl());
			assertNotNull(claimed.getUpdated());

			// a site published again under another sites domain moves the primary UI's address, and only the primary UI's
			assertFalse(await(applicationRepository.updatePrimaryUiUrl(key, "admin", "https://primary-claim--admin.test-org.example.net")));
			assertTrue(await(applicationRepository.updatePrimaryUiUrl(key, "web", "https://primary-claim--web.test-org.example.net")));
			assertFalse(await(applicationRepository.updatePrimaryUiUrl(key, "web", "https://primary-claim--web.test-org.example.net")));
			Application moved = await(applicationRepository.findById(created.getId(), TEST_ORG_ID));
			assertEquals("web", moved.getPrimaryUiId());
			assertEquals("https://primary-claim--web.test-org.example.net", moved.getPrimaryUiUrl());
		} finally {
			await(runAsOrganization(() -> applicationService.deleteById(created.getId())));
		}
	}

}
