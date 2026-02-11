package org.kinotic.structures.internal.sample;

import jakarta.annotation.PostConstruct;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.internal.api.domain.DefaultEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/1/23.
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TestDataService testDataService;
    private final ObjectMapper objectMapper;
    private final EntitiesService entitiesService;
    private final StructuresProperties properties;

    public DataInitializer(TestDataService testDataService,
                           ObjectMapper objectMapper,
                           EntitiesService entitiesService,
                           StructuresProperties properties) {
        this.testDataService = testDataService;
        this.objectMapper = objectMapper;
        this.entitiesService = entitiesService;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.isInitializeWithSampleData()) {
            log.info("*** Initializing sample data ***");
            testDataService.createPersonStructureIfNotExists()
                           .thenCompose(structureBooleanPair -> {
                               if(structureBooleanPair.getRight()) {
                                   return CompletableFuture.allOf(createPeople(structureBooleanPair.getLeft(),
                                                                               300,
                                                                               "kinotic",
                                                                               "structures"),
                                                                  createPeople(structureBooleanPair.getLeft(),
                                                                               250,
                                                                               "kinotic2",
                                                                               "structures2"));
                               } else {
                                   return null;
                               }
                           }).thenApply(aVoid -> null);
        }
    }

    private CompletableFuture<Void> createPeople(Structure structure,
                                                 int numberOfPeopleToCreate,
                                                 String tenantId,
                                                 String participantId){
        return testDataService.createRandomTestPeople(numberOfPeopleToCreate)
                              .thenCompose(people -> {
                                  TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
                                  tokenBuffer.writePOJO(people);
                                  return entitiesService.bulkSave(structure.getId(),
                                                                  tokenBuffer,
                                                                  new DefaultEntityContext(new DummyParticipant(tenantId,
                                                                                                                participantId)));
                              });
    }


}
