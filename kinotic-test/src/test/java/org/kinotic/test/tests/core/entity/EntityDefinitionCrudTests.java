

package org.kinotic.test.tests.core.entity;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.converter.C3ConversionException;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// FIXME: Migrate to E2E tests
@SpringBootTest
public class EntityDefinitionCrudTests extends KinoticTestBase {

	@Autowired
	private EntityDefinitionService entityDefinitionService;
	@Autowired
	private TestDataService testDataService;
	@Autowired
	private EntitiesService entitiesService;

	@Test
	public void createPublishAndDeleteStructure() throws Exception {

		Thread.sleep(2000);

		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonWat")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId(TEST_APP_ID)
						.setProjectId(TestDataService.SAMPLE_PROJECT_ID)
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		Future<EntityDefinition> future = runAsOrganization(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromCompletionStage(future.toCompletionStage()))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(entityDefinition.getName(), savedStructure.getName());
						Assertions.assertEquals(entityDefinition.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(entityDefinition.getSchema(), savedStructure.getSchema());
						return true;
					})
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.publish(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.unPublish(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.deleteById(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationsOnPublishedStructure() throws Exception{
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonBum")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId(TEST_APP_ID)
						.setProjectId(TestDataService.SAMPLE_PROJECT_ID)
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		Future<EntityDefinition> future = runAsOrganization(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromCompletionStage(future.toCompletionStage()))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(entityDefinition.getName(), savedStructure.getName());
						Assertions.assertEquals(entityDefinition.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(entityDefinition.getSchema(), savedStructure.getSchema());
						return true;
					})
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.publish(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.deleteById(future.await().getId())).toCompletionStage()))
					.expectError(IllegalStateException.class)
					.verify();

		// TODO: add rename name and application operations
	}

	@Test
	public void createStructureInvalidField(){
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStupid")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId(TEST_APP_ID)
						.setProjectId(TestDataService.SAMPLE_PROJECT_ID)
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, true));

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.create(entityDefinition)).toCompletionStage()))
					.expectError(C3ConversionException.class)
					.verify();
	}

	@Test
	public void createStructureWithSameNameError() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonHomer")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId(TEST_APP_ID)
						.setProjectId(TestDataService.SAMPLE_PROJECT_ID)
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		Future<EntityDefinition> future = runAsOrganization(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromCompletionStage(future.toCompletionStage()))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(entityDefinition.getName(), savedStructure.getName());
						Assertions.assertEquals(entityDefinition.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(entityDefinition.getSchema(), savedStructure.getSchema());
						return true;
					})
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.create(entityDefinition)).toCompletionStage()))
					.expectError(IllegalArgumentException.class)
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.deleteById(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationOnNotPublishedStructure() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStoned")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId(TEST_APP_ID)
						.setProjectId(TestDataService.SAMPLE_PROJECT_ID)
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		Future<EntityDefinition> future = runAsOrganization(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromCompletionStage(future.toCompletionStage()))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(entityDefinition.getName(), savedStructure.getName());
						Assertions.assertEquals(entityDefinition.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(entityDefinition.getSchema(), savedStructure.getSchema());
						return true;
					})
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(future.await().getId(), new DefaultEntityContext(applicationParticipant()))).toCompletionStage()))
					.expectError(IllegalArgumentException.class)
					.verify();

		StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entityDefinitionService.deleteById(future.await().getId())).toCompletionStage()))
					.expectComplete()
					.verify();
	}
}
