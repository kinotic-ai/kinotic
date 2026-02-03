package org.mindignited.structures.tests.core.entity;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.mindignited.structures.support.elastic.ElasticTestBase;
import org.mindignited.structures.internal.api.domain.DefaultEntityContext;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.services.EntitiesService;
import org.mindignited.structures.internal.sample.Car;
import org.mindignited.structures.internal.sample.DummyParticipant;
import org.mindignited.structures.internal.sample.Person;
import org.mindignited.structures.internal.sample.TestDataService;
import org.mindignited.structures.tests.core.support.StructureAndPersonHolder;
import org.mindignited.structures.tests.core.support.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@SpringBootTest
public class BulkUpdateTests extends ElasticTestBase {

    @Autowired
    private EntitiesService entitiesService;
    @Autowired
    private TestHelper testHelper;
    @Autowired
    private TestDataService testDataService;

    private StructureAndPersonHolder createAndVerifyBulk(int numberOfPeopleToCreate,
                                                         boolean randomPeople,
                                                         EntityContext entityContext,
                                                         String structureSuffix){
        StructureAndPersonHolder ret = new StructureAndPersonHolder();

        StepVerifier.create(testHelper.createPersonStructureAndEntitiesBulk(numberOfPeopleToCreate,
                                                                            randomPeople,
                                                                            entityContext,
                                                                            structureSuffix))
                    .expectNextMatches(structureAndPersonHolder -> {
                        boolean matches = structureAndPersonHolder.getStructure() != null &&
                                structureAndPersonHolder.getStructure().getId() != null &&
                                structureAndPersonHolder.getPersons().size() == numberOfPeopleToCreate;
                        if(matches){
                            ret.setStructure(structureAndPersonHolder.getStructure());
                            ret.setPersons(structureAndPersonHolder.getPersons());
                        }
                        return matches;
                    })
                    .verifyComplete();
        return ret;
    }

    @Test
    public void testBulk() throws InterruptedException{
        int numberOfPeopleToCreate = 50;
        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = createAndVerifyBulk(numberOfPeopleToCreate, true, context1, "_testBulk");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = createAndVerifyBulk(numberOfPeopleToCreate, true, context2, "_testBulk");

        Assertions.assertNotNull(holder2);

        // Sync Index since bulk updates are not queryable until they are indexed
        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        // TODO: verify all data items as well, not just sizes
        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder1.getStructure().getId(),
                                                                    Pageable.ofSize(numberOfPeopleToCreate * 2),// make sure page size is larger than number of entities
                                                                    RawJson.class,
                                                                    context1)))
                    .expectNextMatches(rawJsons -> rawJsons.getTotalElements() == numberOfPeopleToCreate
                            && rawJsons.getContent().size() == numberOfPeopleToCreate)
                    .as("Verifying Tenant 1 has "+numberOfPeopleToCreate+" entities")
                    .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.ofSize(numberOfPeopleToCreate * 2), // make sure page size is larger than number of entities
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(rawJsons -> rawJsons.getTotalElements() == numberOfPeopleToCreate
                            && rawJsons.getContent().size() == numberOfPeopleToCreate)
                    .as("Verifying Tenant 2 has "+numberOfPeopleToCreate+" entities")
                    .verifyComplete();
    }

    @Test
    public void bulkSaveObjectWithMultipleIds() throws Exception{
        EntityContext entityContext = new DefaultEntityContext(new DummyParticipant());
        CompletableFuture<Pair<Structure, Boolean>> createStructure = testDataService.createCarStructureIfNotExists("_bulkSaveMultipleIds");

        StepVerifier.create(Mono.fromFuture(createStructure))
                    .expectNextMatches(pair -> pair.getLeft() != null && pair.getRight())
                    .verifyComplete();

        Structure structure = createStructure.join().getLeft();

        List<Person> personList = testDataService.createRandomTestPeopleWithId(50).join();

        Assertions.assertEquals(50, personList.size(), "Failed to create test person");

        List<Car> cars = new ArrayList<>(50);
        for(Person person : personList){
            int count = cars.size();
            Car car = new Car();
            car.setId(UUID.randomUUID().toString());
            car.setMake("Honda-"+count);
            car.setModel("Civic-"+count);
            car.setYear(2019);
            car.setOwner(person);
            cars.add(car);
        }

        testHelper.bulkSaveCarsAsRawJson(cars, structure, entityContext).join();

        // Sync Index since bulk updates are not queryable until they are indexed
        entitiesService.syncIndex(structure.getId(), entityContext).join();

        Page<RawJson> page = entitiesService.findAll(structure.getId(), Pageable.ofSize(10), RawJson.class, entityContext).join();

        Assertions.assertEquals(50, page.getTotalElements(), "Wrong number of entities");
    }

    @Test
    public void bulkUpdateObjectWithMultipleIds() throws Exception{
        EntityContext entityContext = new DefaultEntityContext(new DummyParticipant());
        CompletableFuture<Pair<Structure, Boolean>> createStructure = testDataService.createCarStructureIfNotExists("_bulkUpdateMultipleIds");

        StepVerifier.create(Mono.fromFuture(createStructure))
                    .expectNextMatches(pair -> pair.getLeft() != null && pair.getRight())
                    .verifyComplete();

        Structure structure = createStructure.join().getLeft();

        List<Person> personList = testDataService.createRandomTestPeopleWithId(50).join();

        Assertions.assertEquals(50, personList.size(), "Failed to create test person");

        List<Car> cars = new ArrayList<>(50);
        for(Person person : personList){
            int count = cars.size();
            Car car = new Car();
            car.setId(UUID.randomUUID().toString());
            car.setMake("Honda-"+count);
            car.setModel("Civic-"+count);
            car.setYear(2019);
            car.setOwner(person);
            cars.add(car);
        }

        testHelper.bulkUpdateCarsAsRawJson(cars, structure, entityContext).join();

        // Sync Index since bulk updates are not queryable until they are indexed
        entitiesService.syncIndex(structure.getId(), entityContext).join();

        Page<RawJson> page = entitiesService.findAll(structure.getId(), Pageable.ofSize(10), RawJson.class, entityContext).join();

        Assertions.assertEquals(50, page.getTotalElements(), "Wrong number of entities");
    }

}
