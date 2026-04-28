

package org.kinotic.test.tests.core.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.sample.DummyParticipant;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

// FIXME: Migrate to E2E tests
@SpringBootTest
public class EntityDefinitionCrudTests extends KinoticTestBase {

	@Autowired
	private EntityDefinitionService entityDefinitionService;
	@Autowired
	private TestDataService testDataService;
	@Autowired
	private EntitiesRepository entitiesRepository;

	@Test
	public void createPublishAndDeleteStructure() throws Exception {

		Thread.sleep(2000);

		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonWat")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = elevated(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromFuture(future))
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

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.publish(future.join().getId()))))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.unPublish(future.join().getId()))))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.deleteById(future.join().getId()))))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationsOnPublishedStructure() throws Exception{
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonBum")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = elevated(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromFuture(future))
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

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.publish(future.join().getId()))))
					.expectComplete()
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.deleteById(future.join().getId()))))
					.expectError(IllegalStateException.class)
					.verify();

		// TODO: add rename name and application operations
	}

	@Test
	public void createStructureInvalidField(){
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStupid")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, true));

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.create(entityDefinition))))
					.expectError(IllegalArgumentException.class)
					.verify();
	}

	@Test
	public void createStructureWithSameNameError() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonHomer")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = elevated(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromFuture(future))
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

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.create(entityDefinition))))
					.expectError(IllegalArgumentException.class)
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.deleteById(future.join().getId()))))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationOnNotPublishedStructure() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStoned")
						.setOrganizationId(TEST_ORG_ID)
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = elevated(() -> entityDefinitionService.create(entityDefinition));

		StepVerifier.create(Mono.fromFuture(future))
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

		StepVerifier.create(Mono.fromFuture(elevated(() -> entitiesRepository.count(future.join().getId(), new DefaultEntityContext(new DummyParticipant())))))
					.expectError(IllegalArgumentException.class)
					.verify();

		StepVerifier.create(Mono.fromFuture(elevated(() -> entityDefinitionService.deleteById(future.join().getId()))))
					.expectComplete()
					.verify();
	}
}
