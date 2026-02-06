package org.kinotic.structures.tests.core.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.internal.api.domain.DefaultEntityContext;
import org.kinotic.structures.internal.sample.Car;
import org.kinotic.structures.internal.sample.DummyParticipant;
import org.kinotic.structures.internal.sample.Person;
import org.kinotic.structures.internal.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Component
public class TestHelper {

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntitiesService entitiesService;


    public StructureAndPersonHolder createAndVerify(){
        return this.createAndVerify(1,
                               true,
                               new DefaultEntityContext(new DummyParticipant()),
                               "_" + System.currentTimeMillis());
    }

    public StructureAndPersonHolder createAndVerify(int numberOfPeopleToCreate,
                                                       boolean randomPeople,
                                                       EntityContext entityContext,
                                                       String structureSuffix){
        StructureAndPersonHolder ret = new StructureAndPersonHolder();

        StepVerifier.create(this.createPersonStructureAndEntities(numberOfPeopleToCreate,
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

    public CompletableFuture<Void> bulkUpdateCarsAsRawJson(List<Car> cars, Structure structure, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false);
        try {
            tokenBuffer.writeObject(cars);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return entitiesService.bulkUpdate(structure.getId(), tokenBuffer, entityContext);
    }

    public CompletableFuture<Void> bulkSaveCarsAsRawJson(List<Car> cars, Structure structure, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false);
        try {
            tokenBuffer.writeObject(cars);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return entitiesService.bulkSave(structure.getId(), tokenBuffer, entityContext);
    }

    public CompletableFuture<Car> saveCarAsRawJson(Car car, Structure structure, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false);
        try {
            tokenBuffer.writeObject(car);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return entitiesService.save(structure.getId(), tokenBuffer, entityContext)
                              .thenApply(saved -> {
                                  try (JsonParser parser = saved.asParser()) {
                                      return objectMapper.readValue(parser, Car.class);
                                  } catch (IOException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
    }


    public CompletableFuture<Car> updateCarAsRawJson(Car car, Structure structure, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false);
        try {
            tokenBuffer.writeObject(car);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        return entitiesService.update(structure.getId(), tokenBuffer, entityContext)
                              .thenApply(saved -> {
                                  try (JsonParser parser = saved.asParser()) {
                                      return objectMapper.readValue(parser, Car.class);
                                  } catch (IOException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
    }

    /**
     * Creates a {@link Structure} if it does not exist with the given name suffix and then creates the given number of people
     * @param numberOfPeopleToCreate the number of people to create
     * @param randomPeople if true random people will be created, if false a static list of people will be created
     * @param entityContext the {@link EntityContext} to use when creating the entities
     * @param structureNameSuffix the suffix to append to the structure name or null to not append anything
     * @return a {@link Mono} that will emit a {@link StructureAndPersonHolder} when complete
     */
    public Mono<StructureAndPersonHolder> createPersonStructureAndEntities(int numberOfPeopleToCreate,
                                                                           boolean randomPeople,
                                                                           EntityContext entityContext,
                                                                           String structureNameSuffix){
        return Mono.fromFuture(() -> testDataService
                .createPersonStructureIfNotExists(structureNameSuffix)
                .thenCompose(pair -> createTestPeopleWithCorrectMethod(numberOfPeopleToCreate, randomPeople)
                                             .thenCompose(people -> {
                                                 Structure structure = pair.getLeft();
                                                 List<CompletableFuture<Person>> completableFutures = new ArrayList<>();
                                                 for(Person person : people){
                                                     try (TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false)) {
                                                         tokenBuffer.writeObject(person);
                                                         completableFutures.add(entitiesService.save(structure.getId(),
                                                                                                     tokenBuffer,
                                                                                                     entityContext)
                                                                                               .thenCompose(saved -> {
                                                                                                   try (JsonParser parser = saved.asParser()) {
                                                                                                       Person deserializedPerson = objectMapper.readValue(parser,
                                                                                                                                                          Person.class);
                                                                                                       return CompletableFuture.completedFuture(deserializedPerson);
                                                                                                   } catch (IOException e) {
                                                                                                       return CompletableFuture.failedFuture(e);
                                                                                                   }
                                                                                               }));
                                                     } catch (IOException e) {
                                                         return CompletableFuture.failedFuture(e);
                                                     }
                                                 }
                                                 return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
                                                                         .thenApply(v -> {
                                                                             StructureAndPersonHolder holder = new StructureAndPersonHolder();
                                                                             holder.setStructure(structure);
                                                                             for(CompletableFuture<Person> future : completableFutures){
                                                                                 holder.addPerson(future.join());
                                                                             }
                                                                             return holder;
                                                                         });
                                             })));
    }

    public Mono<StructureAndPersonHolder> createPersonStructureAndEntitiesBulk(int numberOfPeopleToCreate,
                                                                               boolean randomPeople,
                                                                               EntityContext entityContext,
                                                                               String structureNameSuffix){
        return Mono.fromFuture(() -> testDataService
                .createPersonStructureIfNotExists(structureNameSuffix)
                .thenCompose(pair -> createTestPeopleWithCorrectMethod(numberOfPeopleToCreate, randomPeople)
                                             .thenCompose(people -> {
                                                 Structure structure = pair.getLeft();
                                                 TokenBuffer tokenBuffer = new TokenBuffer(objectMapper, false);
                                                 try {
                                                     tokenBuffer.writeObject(people);
                                                 } catch (IOException e) {
                                                     return CompletableFuture.failedFuture(e);
                                                 }

                                                 return entitiesService.bulkSave(structure.getId(),
                                                                                 tokenBuffer,
                                                                                 entityContext)
                                                         .thenCompose(unused -> CompletableFuture
                                                                 .completedFuture(new StructureAndPersonHolder(
                                                                         structure,
                                                                         people)));
                                             })));
    }


    private CompletableFuture<List<Person>> createTestPeopleWithCorrectMethod(int numberOfPeopleToCreate,
                                                                              boolean randomPeople){
        if(randomPeople){
            return testDataService.createRandomTestPeople(numberOfPeopleToCreate);
        }else {
            return testDataService.createTestPeople(numberOfPeopleToCreate);
        }
    }


}
