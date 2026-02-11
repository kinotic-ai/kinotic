

package org.kinotic.test.tests.core.entity;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.core.api.crud.CursorPage;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.core.api.crud.Sort;
import org.kinotic.test.support.elastic.ElasticTestBase;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.domain.RawJson;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.sample.Car;
import org.kinotic.persistence.internal.sample.DummyParticipant;
import org.kinotic.persistence.internal.sample.Person;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.kinotic.test.tests.core.support.StructureAndPersonHolder;
import org.kinotic.test.tests.core.support.TestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
public class EntityCrudTests extends ElasticTestBase {

    private static final Logger log = LoggerFactory.getLogger(EntityCrudTests.class);

    @Autowired
    private EntitiesService entitiesService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataService testDataService;
    @Autowired
    private TestHelper testHelper;

    @Test
    public void testCreateAndDeleteItem() {

        StructureAndPersonHolder holder = testHelper.createAndVerify();

        Assertions.assertNotNull(holder);

        StepVerifier.create(Mono.fromFuture(entitiesService.deleteById(holder.getStructure().getId(),
                                                                       holder.getFirstPerson().getId(),
                                                                       new DefaultEntityContext(new DummyParticipant()))))
                    .verifyComplete();
    }

    @Test
    public void testCreateAndDeleteByQuery() {
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant", "user"));

        StructureAndPersonHolder holder = testHelper.createAndVerify(20, false, context, "_testFindByIds");

        Assertions.assertNotNull(holder);

        entitiesService.syncIndex(holder.getStructure().getId(), context).join();

        StepVerifier.create(Mono.fromFuture(entitiesService.count(holder.getStructure().getId(), context)))
                .expectNext(20L)
                .as("Verifying Tenant 1 has 20 entities")
                .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.deleteByQuery(holder.getStructure().getId(),
                        "lastName: A*",
                        context)))
                .verifyComplete();

        entitiesService.syncIndex(holder.getStructure().getId(), context).join();

        StepVerifier.create(Mono.fromFuture(entitiesService.count(holder.getStructure().getId(), context)))
                .expectNext(18L)
                .as("Verifying Tenant 1 has 18 entities after delete by query")
                .verifyComplete();

    }


    @Test
    public void testFindById(){
        StructureAndPersonHolder holder = testHelper.createAndVerify();

        Assertions.assertNotNull(holder);

        StepVerifier.create(Mono.fromFuture(entitiesService.findById(holder.getStructure().getId(),
                                                                     holder.getFirstPerson().getId(),
                                                                     RawJson.class,
                                                                     new DefaultEntityContext(new DummyParticipant()))))
                    .expectNextMatches(found -> {
                        boolean ret;
                        try {
                            Person savedPerson = objectMapper.readValue(found.data(),
                                                                        Person.class);
                            Assertions.assertNotNull(savedPerson);

                            ret = savedPerson.equals(holder.getFirstPerson());

                        } catch (JacksonException e) {
                            throw new RuntimeException(e);
                        }
                        return ret;
                    })
                    .verifyComplete();

    }

    @Test
    public void testFindByIds(){
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant", "user"));

        StructureAndPersonHolder holder = testHelper.createAndVerify(10, true, context, "_testFindByIds");

        Assertions.assertNotNull(holder);

        HashMap<String, Person> personMap = new HashMap<>();
        ArrayList<String> ids = new ArrayList<>();
        for(int i = 0; i < holder.getPersons().size(); i++){
            Person p = holder.getPersons().get(i);
            personMap.put(p.getId(), p);
            if(i % 2 == 0){
                ids.add(p.getId());
            }
        }

        StepVerifier.create(Mono.fromFuture(entitiesService.findByIds(holder.getStructure().getId(),
                        ids,
                        RawJson.class,
                        context)))
                .expectNextMatches(responseList -> {
                    boolean ret = false;
                    try {
                        Assertions.assertEquals(ids.size(), responseList.size(), "id list size does not match response list");
                        for(RawJson json : responseList){
                            Person savedPerson = objectMapper.readValue(json.data(),
                                    Person.class);
                            Assertions.assertNotNull(savedPerson);
                            ret = savedPerson.equals(personMap.get(savedPerson.getId()));
                            if(!ret){
                                break;
                            }
                        }
                    } catch (JacksonException e) {
                        throw new RuntimeException(e);
                    }
                    return ret;
                })
                .verifyComplete();

    }

    @Test
    public void testFindByIdsNoneFound(){
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant", "user"));

        StructureAndPersonHolder holder = testHelper.createAndVerify(10, true, context, "_testFindByIdsNoneFound");

        Assertions.assertNotNull(holder);

        ArrayList<String> ids = new ArrayList<>();
        for(int i = 0; i < holder.getPersons().size(); i++){
            Person p = holder.getPersons().get(i);
            if(i % 2 == 0){
                ids.add("aaaaa"+p.getId()+"aaaaa");
            }
        }

        StepVerifier.create(Mono.fromFuture(entitiesService.findByIds(holder.getStructure().getId(),
                        ids,
                        RawJson.class,
                        context)))
                .expectNextMatches(List::isEmpty)
                .as("Verifying Tenant ids query to be empty as none of ids matches")
                .verifyComplete();

    }

    @Test
    public void testCount(){
        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = testHelper.createAndVerify(10, true, context1, "_testCount");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = testHelper.createAndVerify(20, true, context2, "_testCount");

        Assertions.assertNotNull(holder2);

        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        StepVerifier.create(Mono.fromFuture(entitiesService.count(holder1.getStructure().getId(), context1)))
                    .expectNext(10L)
                    .as("Verifying Tenant 1 has 10 entities")
                    .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.count(holder2.getStructure().getId(), context2)))
                    .expectNext(20L)
                    .as("Verifying Tenant 2 has 20 entities")
                    .verifyComplete();
    }


    @Test
    public void testCountByQuery(){
        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = testHelper.createAndVerify(10, false, context1, "_testCountByQuery");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = testHelper.createAndVerify(20, false, context2, "_testCountByQuery");

        Assertions.assertNotNull(holder2);

        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        StepVerifier.create(Mono.fromFuture(entitiesService.countByQuery(holder1.getStructure().getId(), "lastName: Z*", context1)))
                .expectNext(2L)
                .as("Verifying Tenant 1 has 2 entities by search")
                .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.countByQuery(holder2.getStructure().getId(), "lastName: A*", context2)))
                .expectNext(2L)
                .as("Verifying Tenant 2 has 2 entities by search")
                .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.countByQuery(holder2.getStructure().getId(), "lastName: a*", context2)))
                .expectNext(0L)
                .as("Verifying Tenant 0 has 2 entities by search")
                .verifyComplete();

    }


    @Test
    public void testFindAll(){

        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = testHelper.createAndVerify(10, true, context1, "_testAll");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = testHelper.createAndVerify(20, true, context2, "_testAll");

        Assertions.assertNotNull(holder2);

        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        // TODO: verify all data items as well, not just sizes
        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder1.getStructure().getId(),
                                                                    Pageable.ofSize(20), // make sure page size is larger than number of entities
                                                                    RawJson.class,
                                                                    context1)))
                    .expectNextMatches(rawJsons -> rawJsons.getTotalElements() == 10
                            && rawJsons.getContent().size() == 10)
                    .as("Verifying Tenant 1 has 10 entities")
                    .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.ofSize(20), // make sure page size is larger than number of entities
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(rawJsons -> rawJsons.getTotalElements() == 20
                            && rawJsons.getContent().size() == 20)
                    .as("Verifying Tenant 2 has 20 entities")
                    .verifyComplete();
    }

    //@Test FIXME: figure out why this test is unreliable
    public void testFindAllWithCursor() throws InterruptedException{

        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = testHelper.createAndVerify(40, true, context1, "_testAllWCursor");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = testHelper.createAndVerify(30, true, context2, "_testAllWCursor");

        Assertions.assertNotNull(holder2);

        // Make sure all data is indexed
        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        Thread.sleep(10000); // TODO: why does this still fail without a sleep? Sync index should be ensuring data is indexed.

        // TODO: verify all data items as well, not just sizes
        Sort sort = Sort.by("firstName");
        AtomicReference<String> cursorRef = new AtomicReference<>();

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder1.getStructure().getId(),
                                                                    Pageable.create("",
                                                                                    20,
                                                                                    Sort.by("firstName")),
                                                                    RawJson.class,
                                                                    context1)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor != null
                                && page.getTotalElements() == null
                                && page.getContent().size() == 20;
                    })
                    .as("Verifying First page for Tenant 1")
                    .verifyComplete();

        Assertions.assertNotNull(cursorRef.get(), "Cursor is null");

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder1.getStructure().getId(),
                                                                    Pageable.create(cursorRef.get(), 20, sort),
                                                                    RawJson.class,
                                                                    context1)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        cursorRef.set(null);
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor != null
                                && page.getTotalElements() ==  null
                                && page.getContent().size() == 20;
                    })
                    .as("Verifying Second page for Tenant 1")
                    .verifyComplete();

        Assertions.assertNotNull(cursorRef.get(), "Cursor is null");

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder1.getStructure().getId(),
                                                                    Pageable.create(cursorRef.get(), 20, sort),
                                                                    RawJson.class,
                                                                    context1)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        cursorRef.set(null);
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor == null
                                && page.getTotalElements() == null
                                && page.getContent().isEmpty();
                    })
                    .as("Verifying Third page is empty for Tenant 1")
                    .verifyComplete();

        // Second tenant

        Assertions.assertNull(cursorRef.get(), "Cursor is not null");

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.create("", 10, sort),
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor != null
                                && page.getTotalElements() == null
                                && page.getContent().size() == 10;
                    })
                    .as("Verifying First page for Tenant 2")
                    .verifyComplete();

        Assertions.assertNotNull(cursorRef.get(), "Cursor is null");

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.create(cursorRef.get(), 10, sort),
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        cursorRef.set(null);
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor != null
                                && page.getTotalElements() == null
                                && page.getContent().size() == 10;
                    })
                    .as("Verifying Second page for Tenant 2")
                    .verifyComplete();

        Assertions.assertNotNull(cursorRef.get(), "Cursor is null");

        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.create(cursorRef.get(), 10, sort),
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        cursorRef.set(null);
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor != null
                                && page.getTotalElements() == null
                                && page.getContent().size() == 10;
                    })
                    .as("Verifying Third page for Tenant 2")
                    .verifyComplete();

        Assertions.assertNotNull(cursorRef.get(), "Cursor is null");


        StepVerifier.create(Mono.fromFuture(entitiesService.findAll(holder2.getStructure().getId(),
                                                                    Pageable.create(cursorRef.get(), 10, sort),
                                                                    RawJson.class,
                                                                    context2)))
                    .expectNextMatches(page -> {
                        String cursor = null;
                        cursorRef.set(null);
                        if(page instanceof CursorPage){
                            cursor = ((CursorPage<?>)page).getCursor();
                            cursorRef.set(cursor);
                        }
                        return cursor == null
                                && page.getTotalElements() == null
                                && page.getContent().isEmpty();
                    })
                    .as("Verifying Fourth page is empty for Tenant 2")
                    .verifyComplete();
    }

    @Test
    public void testSearch(){
        EntityContext context1 = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));
        EntityContext context2 = new DefaultEntityContext(new DummyParticipant("tenant2", "user2"));

        StructureAndPersonHolder holder1 = testHelper.createAndVerify(10, false, context1, "_testSearch");

        Assertions.assertNotNull(holder1);

        StructureAndPersonHolder holder2 = testHelper.createAndVerify(20, false, context2, "_testSearch");

        Assertions.assertNotNull(holder2);

        entitiesService.syncIndex(holder1.getStructure().getId(), context1).join();
        entitiesService.syncIndex(holder2.getStructure().getId(), context2).join();

        // TODO: verify all data items as well, not just sizes
        StepVerifier.create(Mono.fromFuture(entitiesService.search(holder1.getStructure().getId(),
                                                                   "lastName: Z*",
                                                                   Pageable.ofSize(20), // make sure page size is larger than number of entities
                                                                   RawJson.class,
                                                                   context1)))
                    .expectNextMatches(rawJsons -> {
                        boolean b = rawJsons.getTotalElements() == 2
                                && rawJsons.getContent().size() == 2;
                        if(!b){
                            log.error("Wrong Data, Wat! Raw:\n"+rawJsons.getContent());
                        }
                        return b;
                    })
                    .as("Verifying search for Tenant 1 has 2 entities")
                    .verifyComplete();

        StepVerifier.create(Mono.fromFuture(entitiesService.search(holder2.getStructure().getId(),
                                                                   "lastName: Z*",
                                                                   Pageable.ofSize(20), // make sure page size is larger than number of entities
                                                                   RawJson.class,
                                                                   context2)))
                    .expectNextMatches(rawJsons -> {
                        boolean b = rawJsons.getTotalElements() == 2
                                && rawJsons.getContent().size() == 2;
                        if(!b){
                            log.error("Wrong Data, Wat! Raw:\n"+rawJsons.getContent());
                        }
                        return b;
                    })
                    .as("Verifying search for Tenant 2 has 2 entities")
                    .verifyComplete();
    }


    @Test
    public void testMultiTenantSearch(){

        HashMap<DefaultEntityContext, StructureAndPersonHolder> contextMap = new HashMap<>();
        for(int i = 0; i < 10; i++){
            int numberOfPeople = (int)(Math.random()*100);
            DefaultEntityContext context = new DefaultEntityContext(new DummyParticipant("tenant"+i, "user"+i));
            StructureAndPersonHolder holder = testHelper.createAndVerify(numberOfPeople, true, context, "_testMultiTenantSearch");
            Assertions.assertNotNull(holder);
            contextMap.put(context,  holder);
        }

        contextMap.forEach((context, holder) -> {

            entitiesService.syncIndex(holder.getStructure().getId(), context).join();

            StepVerifier.create(Mono.fromFuture(entitiesService.search(holder.getStructure().getId(),
                            "lastName: *",
                            Pageable.ofSize(20), // make sure page size is larger than number of entities
                            RawJson.class,
                            context)))
                    .expectNextMatches(rawJsons -> {
                        long expectedCount = holder.getPersons().size();
                        long queriedCount = rawJsons.getTotalElements();
                        if(expectedCount != queriedCount){
                            log.error("Wrong Data, Wat! Expected: "+expectedCount+" and Queried: "+queriedCount);
                        }
                        return expectedCount == queriedCount;
                    })
                    .as("Verifying search for Multi Tenant, count queried does not match how many we stored")
                    .verifyComplete();
        });

    }

    @Test
    public void findMissingEntity(){
        StructureAndPersonHolder holder = testHelper.createAndVerify();

        Assertions.assertNotNull(holder);

        StepVerifier.create(Mono.fromFuture(entitiesService.findById(holder.getStructure().getId(),
                                                                     "missing",
                                                                     RawJson.class,
                                                                     new DefaultEntityContext(new DummyParticipant()))))
                    .verifyComplete();
    }

    @Test
    public void testPartialUpdate() throws Exception {
        EntityContext entityContext = new DefaultEntityContext(new DummyParticipant());
        CompletableFuture<Pair<Structure, Boolean>> createStructure = testDataService.createCarStructureIfNotExists("_partialUpdate");

        StepVerifier.create(Mono.fromFuture(createStructure))
                    .expectNextMatches(pair -> {
                        boolean ret = pair.getLeft() != null && pair.getRight();
                        if(!ret){
                            log.error("Failed to create structure: "+pair.getLeft());
                        }
                        return ret;
                    })
                    .verifyComplete();

        Structure structure = createStructure.join().getLeft();

        Car car = new Car();
        car.setId(UUID.randomUUID().toString());
        car.setMake("Honda");
        car.setModel("Civic");
        car.setYear(2019);

        Car result = testHelper.saveCarAsRawJson(car, structure, entityContext).join();

        Assertions.assertEquals(car.getId(), result.getId(), "Car id does not match");

        entitiesService.syncIndex(structure.getId(), entityContext).join();

        Page<RawJson> page = entitiesService.findAll(structure.getId(), Pageable.ofSize(10), RawJson.class, entityContext).join();

        Assertions.assertEquals(1, page.getTotalElements(), "Wrong number of entities");

        List<Person> personList = testDataService.createRandomTestPeopleWithId(1).join();

        Assertions.assertEquals(1, personList.size(), "Failed to create test person");

        Person person = personList.get(0);

        // now do partial update
        car.setMake(null)
           .setModel(null)
           .setYear(null)
           .setOwner(person);

        Car result2 = testHelper.updateCarAsRawJson(car, structure, entityContext).join();

        Assertions.assertEquals(car.getId(), result2.getId(), "Car id does not match after partial update");

        entitiesService.syncIndex(structure.getId(), entityContext).join();

        Page<RawJson> page2 = entitiesService.findAll(structure.getId(), Pageable.ofSize(10), RawJson.class, entityContext).join();

        Assertions.assertEquals(1, page2.getTotalElements(), "Wrong number of entities after partial update");

        Car updatedCar = objectMapper.readValue(page2.getContent().get(0).data(), Car.class);

        Assertions.assertNotNull(updatedCar.getMake(), "Expected make to be null after partial update");
        Assertions.assertNotNull(updatedCar.getModel(), "Expected model to be null after partial update");
        Assertions.assertNotNull(updatedCar.getYear(), "Expected year to be null after partial update");
        Assertions.assertNotNull(updatedCar.getOwner(), "Expected owner to be set after partial update");
    }

}
