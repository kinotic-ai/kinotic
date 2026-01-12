

package org.mindignited.structures.structure;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mindignited.structures.ElasticsearchTestBase;
import org.mindignited.structures.internal.api.domain.DefaultEntityContext;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.MultiTenancyType;
import org.mindignited.structures.api.services.EntitiesService;
import org.mindignited.structures.api.services.StructureService;
import org.mindignited.structures.internal.sample.DummyParticipant;
import org.mindignited.structures.internal.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
public class StructureCrudTests extends ElasticsearchTestBase {

	@Autowired
	private StructureService structureService;
	@Autowired
	private TestDataService testDataService;
	@Autowired
	private EntitiesService entitiesService;

	@Test
	public void createPublishAndDeleteStructure() throws Exception {

		Structure structure = new Structure();
		structure.setName("PersonWat")
				 .setApplicationId("org.kinotic.sample")
				 .setProjectId("org.kinotic.sample_default")
				 .setDescription("Defines a Person")
				 .setEntityDefinition(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<Structure> future = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(structure.getName(), savedStructure.getName());
						Assertions.assertEquals(structure.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(structure.getEntityDefinition(), savedStructure.getEntityDefinition());
						return true;
					})
					.expectComplete()
					.verify();

		CompletableFuture<Void> pubFuture = structureService.publish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(pubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> unPubFuture = structureService.unPublish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(unPubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> delFuture = structureService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationsOnPublishedStructure() throws Exception{
		Structure structure = new Structure();
		structure.setName("PersonBum")
				 .setApplicationId("org.kinotic.sample")
				 .setProjectId("org.kinotic.sample_default")
				 .setDescription("Defines a Person")
				 .setEntityDefinition(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<Structure> future = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(structure.getName(), savedStructure.getName());
						Assertions.assertEquals(structure.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(structure.getEntityDefinition(), savedStructure.getEntityDefinition());
						return true;
					})
					.expectComplete()
					.verify();

		CompletableFuture<Void> pubFuture = structureService.publish(future.get().getId());

		StepVerifier.create(Mono.fromFuture(pubFuture))
					.expectComplete()
					.verify();

		CompletableFuture<Void> delFuture = structureService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectError(IllegalStateException.class)
					.verify();

		// TODO: add rename name and application operations
	}

	@Test
	public void createStructureInvalidField(){
		Structure structure = new Structure();
		structure.setName("PersonStupid")
				 .setApplicationId("org.kinotic.sample")
				 .setProjectId("org.kinotic.sample_default")
				 .setDescription("Defines a Person")
				 .setEntityDefinition(testDataService.createPersonSchema(MultiTenancyType.NONE, true));

		CompletableFuture<Structure> future = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future))
					.expectError(IllegalArgumentException.class)
					.verify();
	}

	@Test
	public void createStructureWithSameNameError() throws Exception {
		Structure structure = new Structure();
		structure.setName("PersonHomer")
				 .setApplicationId("org.kinotic.sample")
				 .setProjectId("org.kinotic.sample_default")
				 .setDescription("Defines a Person")
				 .setEntityDefinition(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<Structure> future = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(structure.getName(), savedStructure.getName());
						Assertions.assertEquals(structure.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(structure.getEntityDefinition(), savedStructure.getEntityDefinition());
						return true;
					})
					.expectComplete()
					.verify();

		CompletableFuture<Structure> future2 = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future2))
					.expectError(IllegalArgumentException.class)
					.verify();

		CompletableFuture<Void> delFuture = structureService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}

	@Test
	public void tryOperationOnNotPublishedStructure() throws Exception {
		Structure structure = new Structure();
		structure.setName("PersonStoned")
				 .setApplicationId("org.kinotic.sample")
				 .setProjectId("org.kinotic.sample_default")
				 .setDescription("Defines a Person")
				 .setEntityDefinition(testDataService.createPersonSchema(MultiTenancyType.NONE, false));

		CompletableFuture<Structure> future = structureService.create(structure);

		StepVerifier.create(Mono.fromFuture(future))
					.expectNextMatches(savedStructure -> {
						Assertions.assertNotNull(savedStructure.getId());
						Assertions.assertNotNull(savedStructure.getCreated());
						Assertions.assertNotNull(savedStructure.getUpdated());
						Assertions.assertEquals(structure.getName(), savedStructure.getName());
						Assertions.assertEquals(structure.getDescription(), savedStructure.getDescription());
						Assertions.assertEquals(structure.getEntityDefinition(), savedStructure.getEntityDefinition());
						return true;
					})
					.expectComplete()
					.verify();

		CompletableFuture<Long> countFuture = entitiesService.count(future.get().getId(), new DefaultEntityContext(new DummyParticipant()));

		StepVerifier.create(Mono.fromFuture(countFuture))
					.expectError(IllegalArgumentException.class)
					.verify();

		CompletableFuture<Void> delFuture = structureService.deleteById(future.get().getId());

		StepVerifier.create(Mono.fromFuture(delFuture))
					.expectComplete()
					.verify();
	}
}
