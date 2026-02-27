

package org.kinotic.test.tests.core.structure;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.test.support.elastic.ElasticTestBase;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.sample.DummyParticipant;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// FIXME: Migrate to E2E tests
@SpringBootTest
public class EntityDefinitionCrudTests extends ElasticTestBase {

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
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = entityDefinitionService.create(entityDefinition);

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

		CompletableFuture<Void> pubFuture = entityDefinitionService.publish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(pubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> unPubFuture = entityDefinitionService.unPublish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(unPubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> delFuture = entityDefinitionService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationsOnPublishedStructure() throws Exception{
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonBum")
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = entityDefinitionService.create(entityDefinition);

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

		CompletableFuture<Void> pubFuture = entityDefinitionService.publish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(pubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> delFuture = entityDefinitionService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectError(IllegalStateException.class)
					.verify();

		// TODO: add rename name and application operations
	}

	@Test
	public void createStructureInvalidField(){
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStupid")
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, true));

		CompletableFuture<EntityDefinition> future = entityDefinitionService.create(entityDefinition);

		StepVerifier.create(Mono.fromFuture(future))
					.expectError(IllegalArgumentException.class)
					.verify();
	}

	@Test
	public void createStructureWithSameNameError() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonHomer")
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = entityDefinitionService.create(entityDefinition);

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

		CompletableFuture<EntityDefinition> future2 = entityDefinitionService.create(entityDefinition);

		StepVerifier.create(Mono.fromFuture(future2))
					.expectError(IllegalArgumentException.class)
					.verify();

		CompletableFuture<Void> delFuture = entityDefinitionService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationOnNotPublishedStructure() throws Exception {
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setName("PersonStoned")
						.setApplicationId("org.kinotic.sample")
						.setProjectId("org.kinotic.sample_default")
						.setDescription("Defines a Person")
						.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<EntityDefinition> future = entityDefinitionService.create(entityDefinition);

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

		CompletableFuture<Long> countFuture = entitiesService.count(future.get().getId(), new DefaultEntityContext(new DummyParticipant()));

		StepVerifier.create(Mono.fromFuture(countFuture))
					.expectError(IllegalArgumentException.class)
					.verify();

		CompletableFuture<Void> delFuture = entityDefinitionService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}
}
