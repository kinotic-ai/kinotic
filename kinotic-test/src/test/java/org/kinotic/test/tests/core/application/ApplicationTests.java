

package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.exceptions.BetaLimitException;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.management.api.config.BetaLimitsProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ApplicationTests extends KinoticTestBase {

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private KinoticManagementApiProperties properties;

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


	@Test
	public void aNewApplicationPastTheBetaLimitIsRefusedAndAnExistingOneStillUpdates() throws Exception {
		BetaLimitsProperties betaLimits = properties.getManagementApi().getBetaLimits();
		String suffix = suffix();
		Application first = await(runAsOrganization(() -> applicationService.create(new Application("Beta One " + suffix, "Within the limit")
																							 .setOrganizationId(TEST_ORG_ID))));
		try {
			// the shared test organization holds other tests' applications, so the cap is the count it has now;
			// the index is refreshed first so an application another test just deleted is not still counted
			await(runAsOrganization(() -> applicationService.syncIndex()));
			long count = await(runAsOrganization(() -> applicationService.count()));
			betaLimits.setMaxApplicationsPerOrganization((int) count);

			ExecutionException refused = assertThrows(ExecutionException.class,
													  () -> await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Beta Two " + suffix, "Past the limit", null))));
			assertInstanceOf(BetaLimitException.class, refused.getCause());
			assertTrue(refused.getCause().getMessage().contains("Kinotic beta cloud"));
			assertTrue(refused.getCause().getMessage().contains("paid plans will let you scale well beyond these limits"));

			// an application that exists is returned and updated, whatever the cap
			assertEquals(first.getId(), await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Beta One " + suffix, "Again", null))).getId());
			first.setDescription("Updated within the limit");
			assertEquals("Updated within the limit", await(runAsOrganization(() -> applicationService.save(first))).getDescription());
		} finally {
			betaLimits.setMaxApplicationsPerOrganization(null);
			await(runAsOrganization(() -> applicationService.deleteById(first.getId())));
		}
	}

}
