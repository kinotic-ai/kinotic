package org.mindignited.structures.internal.sample;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import org.apache.commons.lang3.tuple.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kinotic.continuum.idl.api.schema.ArrayC3Type;
import org.kinotic.continuum.idl.api.schema.IntC3Type;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.continuum.idl.api.schema.StringC3Type;
import org.kinotic.continuum.internal.utils.ContinuumUtil;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.*;
import org.mindignited.structures.api.services.ApplicationService;
import org.mindignited.structures.api.services.StructureService;
import org.mindignited.structures.auth.internal.services.DefaultCaffeineCacheFactory;
import org.mindignited.structures.internal.utils.StructuresUtil;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/1/23.
 */
@Component
public class TestDataService {

    private static final String PEOPLE_FILE = "classpath:people.json";
    private static final String PEOPLE_WITH_ID_FILE = "classpath:people-with-id.json";
    private static final String PEOPLE_KEY = "people";
    private static final String PEOPLE_WITH_ID_KEY = "people-with-id";

    private final ApplicationService applicationService;
    private final StructureService structureService;

    private final AsyncLoadingCache<String, List<Person>> peopleCache;

    public TestDataService(ApplicationService applicationService,
                           StructureService structureService,
                           ResourceLoader resourceLoader,
                           ObjectMapper objectMapper,
                           DefaultCaffeineCacheFactory cacheFactory) {

        this.applicationService = applicationService;
        this.structureService = structureService;

        peopleCache = cacheFactory.<String, List<Person>>newBuilder()
                              .name("peopleCache")
                              .maximumSize(10)
                              .expireAfterAccess(Duration.ofMinutes(5))
                              .buildAsync(new PeopleCacheLoader(resourceLoader, objectMapper));
    }

    public ObjectC3Type createCarSchema(MultiTenancyType multiTenancyType){
        ObjectC3Type ret = new ObjectC3Type()
                .setName("Car")
                .setNamespace("org.kinotic.sample")
                .addProperty("id", new StringC3Type(), List.of(new IdDecorator()))
                .addProperty("make", new StringC3Type())
                .addProperty("model", new StringC3Type())
                .addProperty("year", new IntC3Type())
                .addProperty("owner", createPersonSchema(multiTenancyType, false, false));

        ret.addDecorator(new EntityDecorator().setMultiTenancyType(multiTenancyType));

        return ret;
    }

    /**
     * Creates a {@link Car} {@link Structure} if it does not exist.
     * @return a {@link CompletableFuture} that will return a {@link Pair} of the {@link Structure} and a {@link Boolean} indicating if the structure was created.
     */
    public CompletableFuture<Pair<Structure, Boolean>> createCarStructureIfNotExists(String structureNameSuffix){
        String structureId = StructuresUtil.structureNameToId("org.kinotic.sample",
                                                              "Car"+(structureNameSuffix != null ? structureNameSuffix : ""));
        return structureService.findById(structureId)
                               .thenCompose(structure -> {
                                   if(structure != null){
                                       return CompletableFuture.completedFuture(Pair.of(structure, false));
                                   }else{
                                       return createCarStructure(structureNameSuffix).thenApply(saved -> Pair.of(saved, true));
                                   }
                               });
    }

    /**
     * Creates a {@link Car} {@link Structure} and publishes it.
     * @param structureNameSuffix if not null will be appended to the structure name.
     * @return a {@link CompletableFuture} that will return the {@link Structure} that was created.
     */
    public CompletableFuture<Structure> createCarStructure(String structureNameSuffix) {
        Structure structure = new Structure();
        structure.setName("Car"+(structureNameSuffix != null ? structureNameSuffix : ""));
        structure.setApplicationId("org.kinotic.sample");
        structure.setProjectId("org.kinotic.sample_default");
        structure.setDescription("Defines a Car");

        ObjectC3Type carType = createCarSchema(MultiTenancyType.SHARED);

        structure.setEntityDefinition(carType);

        return applicationService.createApplicationIfNotExist("org.kinotic.sample", "Sample application")
                               .thenCompose(v -> structureService.create(structure)
                                                                 .thenCompose(saved -> structureService.publish(saved.getId())
                                                                                                       .thenApply(published -> saved)));
    }


    /**
     * @param multiTenancyType the {@link MultiTenancyType} to use for the {@link EntityDecorator}
     * @return a {@link ObjectC3Type} representing a person.
     */
    public ObjectC3Type createPersonSchema(MultiTenancyType multiTenancyType){
        return createPersonSchema(multiTenancyType, false);
    }

    /**
     * @param multiTenancyType the {@link MultiTenancyType} to use for the {@link EntityDecorator}
     * @param addInvalidField  if true an invalid field will be added to the schema
     * @return a {@link ObjectC3Type} representing a person.
     */
    public ObjectC3Type createPersonSchema(MultiTenancyType multiTenancyType, boolean addInvalidField){
        return createPersonSchema(multiTenancyType, addInvalidField, true);
    }

    public ObjectC3Type createPersonSchema(MultiTenancyType multiTenancyType, boolean addInvalidField, boolean createAsEntity){
        StringC3Type idType = new StringC3Type();
        ObjectC3Type ret =  new ObjectC3Type()
                .setName("Person")
                .setNamespace("org.kinotic.sample")
                .addProperty("id", idType, createAsEntity ? List.of(new AutoGeneratedIdDecorator()) : null)
                .addProperty("firstName", new StringC3Type())
                .addProperty("lastName", new StringC3Type())
                .addProperty("addresses", new ArrayC3Type()
                        .setContains(new ObjectC3Type()
                                             .setName("Address")
                                             .addProperty("street", new StringC3Type(), List.of(new TextDecorator()))
                                             .addProperty("city", new StringC3Type())
                                             .addProperty("state", new StringC3Type())
                                             .addProperty("zip"+(addInvalidField ? "." : ""), new StringC3Type())),
                             List.of(new NestedDecorator()));

        if(createAsEntity) {
            ret.addDecorator(new EntityDecorator().setMultiTenancyType(multiTenancyType));
        }
        return ret;
    }

    public CompletableFuture<Pair<Structure, Boolean>> createPersonStructureIfNotExists(){
        return createPersonStructureIfNotExists(null);
    }

    /**
     * Creates a person structure if it does not exist.
     * @return a {@link CompletableFuture} that will return a {@link Pair} of the {@link Structure} and a {@link Boolean} indicating if the structure was created.
     */
    public CompletableFuture<Pair<Structure, Boolean>> createPersonStructureIfNotExists(String structureNameSuffix){
        String structureId = StructuresUtil.structureNameToId("org.kinotic.sample",
                                                              "Person"+(structureNameSuffix != null ? structureNameSuffix : ""));
        return structureService.findById(structureId)
                               .thenCompose(structure -> {
                                   if(structure != null){
                                       return CompletableFuture.completedFuture(Pair.of(structure, false));
                                   }else{
                                       return createPersonStructure(structureNameSuffix).thenApply(saved -> Pair.of(saved, true));
                                   }
                               });
    }

    /**
     * Creates a {@link Person} {@link Structure} and publishes it.
     * @param structureNameSuffix if not null will be appended to the structure name.
     * @return a {@link CompletableFuture} that will return the {@link Structure} that was created.
     */
    public CompletableFuture<Structure> createPersonStructure(String structureNameSuffix) {
        Structure structure = new Structure();
        structure.setName("Person"+(structureNameSuffix != null ? structureNameSuffix : ""));
        structure.setApplicationId("org.kinotic.sample");
        structure.setProjectId("org.kinotic.sample_default");
        structure.setDescription("Defines a Person");

        ObjectC3Type personType = createPersonSchema(MultiTenancyType.SHARED);

        structure.setEntityDefinition(personType);

        return applicationService.createApplicationIfNotExist("org.kinotic.sample", "Sample application")
                               .thenCompose(v -> structureService.create(structure)
                                                                 .thenCompose(saved -> structureService.publish(saved.getId())
                                                                                                       .thenApply(published -> saved)));
    }

    /**
     * @return a {@link CompletableFuture} that will return a random {@link Person} from the cache.
     */
    public CompletableFuture<Person> createTestPerson() {
        return peopleCache.get(PEOPLE_KEY).thenApply(people -> people.get(ContinuumUtil.getRandomNumberInRange(people.size() - 1)));
    }

    /**
     * @return a {@link CompletableFuture} that will return a random {@link Person} with the id populated, from the cache.
     */
    public CompletableFuture<Person> createTestPersonWithId() {
        return peopleCache.get(PEOPLE_WITH_ID_KEY).thenApply(people -> people.get(ContinuumUtil.getRandomNumberInRange(people.size() - 1)));
    }

    /**
     * Creates a {@link CompletableFuture} that will return a {@link List} of static {@link Person} from the cache.
     * Each call will return the exact same {@link List} of {@link Person}.
     * @param numberToCreate the number of {@link Person} to create.
     * @return a {@link CompletableFuture} that will return a {@link List} of random {@link Person} from the cache.
     */
    public CompletableFuture<List<Person>> createTestPeople(int numberToCreate) {
        return getPeopleCompletableFuture(numberToCreate, false, PEOPLE_KEY);
    }

    /**
     * Creates a {@link CompletableFuture} that will return a {@link List} of static {@link Person} with the id populated, from the cache.
     * Each call will return the exact same {@link List} of {@link Person}.
     * @param numberToCreate the number of {@link Person} with the id populated, to create.
     * @return a {@link CompletableFuture} that will return a {@link List} of random {@link Person} with the id populated, from the cache.
     */
    public CompletableFuture<List<Person>> createTestPeopleWithId(int numberToCreate) {
        return getPeopleCompletableFuture(numberToCreate, false, PEOPLE_WITH_ID_KEY);
    }

    /**
     * @param numberToCreate the number of random {@link Person} to create.
     * @return a {@link CompletableFuture} that will return a {@link List} of random {@link Person} from the cache.
     */
    public CompletableFuture<List<Person>> createRandomTestPeople(int numberToCreate) {
        return getPeopleCompletableFuture(numberToCreate, true, PEOPLE_KEY);
    }

    /**
     * @param numberToCreate the number of random {@link Person} with the id populated, to create.
     * @return a {@link CompletableFuture} that will return a {@link List} of random {@link Person} with the id populated, from the cache.
     */
    public CompletableFuture<List<Person>> createRandomTestPeopleWithId(int numberToCreate) {
        return getPeopleCompletableFuture(numberToCreate, true, PEOPLE_WITH_ID_KEY);
    }

    private CompletableFuture<List<Person>> getPeopleCompletableFuture(int numberToCreate,
                                                                       boolean random,
                                                                       String peopleKey) {
        return peopleCache.get(peopleKey).thenApply(people -> {
            int size = people.size();
            if(!random && size < numberToCreate){
                throw new IllegalArgumentException("Cannot create "+numberToCreate+" people, a max of "+size+" are available." +
                                                           "\nIf you must create more people, use one of the random variants.");
            }

            List<Person> ret = new ArrayList<>(numberToCreate);
            for(int i = 0; i < numberToCreate; i++) {
                ret.add(people.get((random ? ContinuumUtil.getRandomNumberInRange(size - 1) : i)));
            }
            return ret;
        });
    }

    private static class PeopleCacheLoader implements CacheLoader<String, List<Person>> {

        private final ResourceLoader resourceLoader;
        private final ObjectMapper objectMapper;

        public PeopleCacheLoader(ResourceLoader resourceLoader,
                                 ObjectMapper objectMapper) {

            this.resourceLoader = resourceLoader instanceof PathMatchingResourcePatternResolver 
            ? (PathMatchingResourcePatternResolver) resourceLoader : new PathMatchingResourcePatternResolver(resourceLoader);
            this.objectMapper = objectMapper;
        }

        @Override
        public @Nullable List<Person> load(String key) throws Exception {
            if(key.equals(PEOPLE_KEY)) {
                return objectMapper.readValue(resourceLoader.getResource(PEOPLE_FILE).getInputStream(),
                                              objectMapper.getTypeFactory()
                                                          .constructCollectionType(List.class, Person.class));
            }else if(key.equals(PEOPLE_WITH_ID_KEY)) {
                return objectMapper.readValue(resourceLoader.getResource(PEOPLE_WITH_ID_FILE).getInputStream(),
                                              objectMapper.getTypeFactory()
                                                          .constructCollectionType(List.class, Person.class));
            }else{
                throw new IllegalArgumentException("Unknown key: " + key);
            }
        }
    }

}
