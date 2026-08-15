package org.kinotic.test.tests.core.support;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.sample.Car;
import org.kinotic.persistence.internal.sample.Person;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class TestHelper {

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntitiesService entitiesService;

    @Autowired
    private Vertx vertx;

    @Autowired
    private SecurityContext securityContext;

    /**
     * Runs the supplier on a Vert.x context with
     * {@link KinoticTestBase#TEST_ORGANIZATION_PARTICIPANT} bound, so that org-scoped
     * services resolve {@link KinoticTestBase#TEST_ORG_ID} from the current participant.
     */
    public <T> Future<T> runAsOrganization(Supplier<Future<T>> supplier) {
        Promise<T> promise = Promise.promise();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            securityContext.setParticipant(context, KinoticTestBase.TEST_ORGANIZATION_PARTICIPANT);
            try {
                supplier.get().onComplete(promise);
            } catch (Throwable t) {
                // a service that throws synchronously would otherwise leave the promise uncompleted, hanging the test
                promise.fail(t);
            }
        });
        return promise.future();
    }


    public StructureAndPersonHolder createAndVerify(){
        return this.createAndVerify(1,
                               true,
                               new DefaultEntityContext(KinoticTestBase.applicationParticipant()),
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
                        boolean matches = structureAndPersonHolder.getEntityDefinition() != null &&
                                structureAndPersonHolder.getEntityDefinition().getId() != null &&
                                structureAndPersonHolder.getPersons().size() == numberOfPeopleToCreate;
                        if(matches){
                            ret.setEntityDefinition(structureAndPersonHolder.getEntityDefinition());
                            ret.setPersons(structureAndPersonHolder.getPersons());
                        }
                        return matches;
                    })
                    .verifyComplete();
        return ret;
    }

    public Future<Void> bulkUpdateCarsAsRawJson(List<Car> cars, EntityDefinition entityDefinition, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
        try {
            tokenBuffer.writePOJO(cars);
        } catch (JacksonException e) {
            return Future.failedFuture(e);
        }
        return runAsOrganization(() -> entitiesService.bulkUpdate(entityDefinition.getId(), tokenBuffer, entityContext));
    }

    public Future<Void> bulkSaveCarsAsRawJson(List<Car> cars, EntityDefinition entityDefinition, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
        try {
            tokenBuffer.writePOJO(cars);
        } catch (JacksonException e) {
            return Future.failedFuture(e);
        }
        return runAsOrganization(() -> entitiesService.bulkSave(entityDefinition.getId(), tokenBuffer, entityContext));
    }

    public Future<Car> saveCarAsRawJson(Car car, EntityDefinition entityDefinition, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
        try {
            tokenBuffer.writePOJO(car);
        } catch (JacksonException e) {
            return Future.failedFuture(e);
        }
        return runAsOrganization(() -> entitiesService.save(entityDefinition.getId(), tokenBuffer, entityContext))
                                 .map(saved -> {
                                  try (JsonParser parser = saved.asParser()) {
                                      return objectMapper.readValue(parser, Car.class);
                                  } catch (JacksonException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
    }


    public Future<Car> updateCarAsRawJson(Car car, EntityDefinition entityDefinition, EntityContext entityContext){
        TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
        try {
            tokenBuffer.writePOJO(car);
        } catch (JacksonException e) {
            return Future.failedFuture(e);
        }

        return runAsOrganization(() -> entitiesService.update(entityDefinition.getId(), tokenBuffer, entityContext))
                                 .map(saved -> {
                                  try (JsonParser parser = saved.asParser()) {
                                      return objectMapper.readValue(parser, Car.class);
                                  } catch (JacksonException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
    }

    /**
     * Creates a {@link EntityDefinition} if it does not exist with the given name suffix and then creates the given number of people
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
        return Mono.fromCompletionStage(() -> runAsOrganization(() -> testDataService
                .createPersonEntityDefinitionIfNotExists(structureNameSuffix)
                .compose(pair -> createTestPeopleWithCorrectMethod(numberOfPeopleToCreate, randomPeople)
                                             .compose(people -> {
                                                 EntityDefinition entityDefinition = pair.getLeft();
                                                 List<Future<Person>> futures = new ArrayList<>();
                                                 for(Person person : people){
                                                     try (TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false)) {
                                                         tokenBuffer.writePOJO(person);
                                                         futures.add(entitiesService.save(entityDefinition.getId(),
                                                                                          tokenBuffer,
                                                                                          entityContext)
                                                                                    .compose(saved -> {
                                                                                        try (JsonParser parser = saved.asParser()) {
                                                                                            Person deserializedPerson = objectMapper.readValue(parser,
                                                                                                                                               Person.class);
                                                                                            return Future.succeededFuture(deserializedPerson);
                                                                                        } catch (JacksonException e) {
                                                                                            return Future.failedFuture(e);
                                                                                        }
                                                                                    }));
                                                     } catch (JacksonException e) {
                                                         return Future.failedFuture(e);
                                                     }
                                                 }
                                                 return Future.all(futures)
                                                              .map(cf -> {
                                                                  StructureAndPersonHolder holder = new StructureAndPersonHolder();
                                                                  holder.setEntityDefinition(entityDefinition);
                                                                  for(Person person : cf.<Person>list()){
                                                                      holder.addPerson(person);
                                                                  }
                                                                  return holder;
                                                              });
                                             }))).toCompletionStage());
    }

    public Mono<StructureAndPersonHolder> createPersonStructureAndEntitiesBulk(int numberOfPeopleToCreate,
                                                                               boolean randomPeople,
                                                                               EntityContext entityContext,
                                                                               String structureNameSuffix){
        return Mono.fromCompletionStage(() -> runAsOrganization(() -> testDataService
                .createPersonEntityDefinitionIfNotExists(structureNameSuffix)
                .compose(pair -> createTestPeopleWithCorrectMethod(numberOfPeopleToCreate, randomPeople)
                                             .compose(people -> {
                                                 EntityDefinition entityDefinition = pair.getLeft();
                                                 TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
                                                 try {
                                                     tokenBuffer.writePOJO(people);
                                                 } catch (JacksonException e) {
                                                     return Future.failedFuture(e);
                                                 }

                                                 return entitiesService.bulkSave(entityDefinition.getId(),
                                                                                 tokenBuffer,
                                                                                 entityContext)
                                                                       .map(new StructureAndPersonHolder(entityDefinition,
                                                                                                         people));
                                             }))).toCompletionStage());
    }


    private Future<List<Person>> createTestPeopleWithCorrectMethod(int numberOfPeopleToCreate,
                                                                   boolean randomPeople){
        if(randomPeople){
            return testDataService.createRandomTestPeople(numberOfPeopleToCreate);
        }else {
            return testDataService.createTestPeople(numberOfPeopleToCreate);
        }
    }


}
