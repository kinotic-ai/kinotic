package org.kinotic.persistence.internal.sample;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.IntC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.model.idl.decorators.*;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.utils.PersistenceUtil;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 6/1/23.
 */
@Component
public class TestDataService {

    /**
     * The organization id all sample data belongs to
     */
    public static final String SAMPLE_ORG_ID = "kinotic";

    /**
     * The id of the Application the sample {@link EntityDefinition}s are created under
     */
    public static final String SAMPLE_APP_ID = "org-kinotic-sample";

    /**
     * The id of the Project the sample {@link EntityDefinition}s are created under
     */
    public static final String SAMPLE_PROJECT_ID = SAMPLE_APP_ID + "-default";

    private static final String PEOPLE_FILE = "classpath:people.json";
    private static final String PEOPLE_WITH_ID_FILE = "classpath:people-with-id.json";
    private static final String PEOPLE_KEY = "people";
    private static final String PEOPLE_WITH_ID_KEY = "people-with-id";

    private final ApplicationService applicationService;
    private final EntityDefinitionService entityDefinitionService;
    private final Vertx vertx;

    private final AsyncLoadingCache<String, List<Person>> peopleCache;

    public TestDataService(ApplicationService applicationService,
                           EntityDefinitionService entityDefinitionService,
                           ResourceLoader resourceLoader,
                           ObjectMapper objectMapper,
                           DefaultCaffeineCacheFactory cacheFactory,
                           Vertx vertx) {

        this.applicationService = applicationService;
        this.entityDefinitionService = entityDefinitionService;
        this.vertx = vertx;

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
     * Creates a {@link Car} {@link EntityDefinition} if it does not exist.
     * @return a {@link Future} that will return a {@link Pair} of the {@link EntityDefinition} and a {@link Boolean} indicating if the structure was created.
     */
    public Future<Pair<EntityDefinition, Boolean>> createCarEntityDefinitionIfNotExists(String structureNameSuffix){
        String structureId = PersistenceUtil.createEntityDefinitionId(SAMPLE_ORG_ID,
                                                                      SAMPLE_APP_ID,
                                                                      "Car"+(structureNameSuffix != null ? structureNameSuffix : ""));
        return entityDefinitionService.findById(structureId)
                                      .compose(structure -> {
                                   Future<Pair<EntityDefinition, Boolean>> ret;
                                   if(structure != null){
                                       ret = Future.succeededFuture(Pair.of(structure, false));
                                   }else{
                                       ret = createCarEntityDefinition(structureNameSuffix).map(saved -> Pair.of(saved, true));
                                   }
                                   return ret;
                               });
    }

    /**
     * Creates a {@link Car} {@link EntityDefinition} and publishes it.
     * @param structureNameSuffix if not null will be appended to the structure name.
     * @return a {@link Future} that will return the {@link EntityDefinition} that was created.
     */
    public Future<EntityDefinition> createCarEntityDefinition(String structureNameSuffix) {
        EntityDefinition structure = new EntityDefinition();
        structure.setName("Car"+(structureNameSuffix != null ? structureNameSuffix : ""));
        structure.setOrganizationId(SAMPLE_ORG_ID);
        structure.setApplicationId(SAMPLE_APP_ID);
        structure.setProjectId(SAMPLE_PROJECT_ID);
        structure.setDescription("Defines a Car");

        ObjectC3Type carType = createCarSchema(MultiTenancyType.SHARED);

        structure.setSchema(carType);

        return applicationService.createApplicationIfNotExist(SAMPLE_APP_ID, "Sample application")
                               .compose(v -> entityDefinitionService.create(structure)
                                                                    .compose(saved -> entityDefinitionService.publish(saved.getId())
                                                                                                             .map(saved)));
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

    public Future<Pair<EntityDefinition, Boolean>> createPersonEntityDefinitionIfNotExists(){
        return createPersonEntityDefinitionIfNotExists(null);
    }

    /**
     * Creates a person structure if it does not exist.
     * @return a {@link Future} that will return a {@link Pair} of the {@link EntityDefinition} and a {@link Boolean} indicating if the structure was created.
     */
    public Future<Pair<EntityDefinition, Boolean>> createPersonEntityDefinitionIfNotExists(String structureNameSuffix){
        String structureId = PersistenceUtil.createEntityDefinitionId(SAMPLE_ORG_ID,
                                                                      SAMPLE_APP_ID,
                                                                      "Person"+(structureNameSuffix != null ? structureNameSuffix : ""));
        return entityDefinitionService.findById(structureId)
                                      .compose(structure -> {
                                   Future<Pair<EntityDefinition, Boolean>> ret;
                                   if(structure != null){
                                       ret = Future.succeededFuture(Pair.of(structure, false));
                                   }else{
                                       ret = createPersonEntityDefinition(structureNameSuffix).map(saved -> Pair.of(saved, true));
                                   }
                                   return ret;
                               });
    }

    /**
     * Creates a {@link Person} {@link EntityDefinition} and publishes it.
     * @param structureNameSuffix if not null will be appended to the structure name.
     * @return a {@link Future} that will return the {@link EntityDefinition} that was created.
     */
    public Future<EntityDefinition> createPersonEntityDefinition(String structureNameSuffix) {
        EntityDefinition structure = new EntityDefinition();
        structure.setName("Person"+(structureNameSuffix != null ? structureNameSuffix : ""));
        structure.setOrganizationId(SAMPLE_ORG_ID);
        structure.setApplicationId(SAMPLE_APP_ID);
        structure.setProjectId(SAMPLE_PROJECT_ID);
        structure.setDescription("Defines a Person");

        ObjectC3Type personType = createPersonSchema(MultiTenancyType.SHARED);

        structure.setSchema(personType);

        return applicationService.createApplicationIfNotExist(SAMPLE_APP_ID, "Sample application")
                               .compose(v -> entityDefinitionService.create(structure)
                                                                    .compose(saved -> entityDefinitionService.publish(saved.getId())
                                                                                                             .map(saved)));
    }

    /**
     * @return a {@link Future} that will return a random {@link Person} from the cache.
     */
    public Future<Person> createTestPerson() {
        return loadPeople(PEOPLE_KEY).map(people -> people.get(KinoticUtil.getRandomNumberInRange(people.size() - 1)));
    }

    /**
     * @return a {@link Future} that will return a random {@link Person} with the id populated, from the cache.
     */
    public Future<Person> createTestPersonWithId() {
        return loadPeople(PEOPLE_WITH_ID_KEY).map(people -> people.get(KinoticUtil.getRandomNumberInRange(people.size() - 1)));
    }

    /**
     * Creates a {@link Future} that will return a {@link List} of static {@link Person} from the cache.
     * Each call will return the exact same {@link List} of {@link Person}.
     * @param numberToCreate the number of {@link Person} to create.
     * @return a {@link Future} that will return a {@link List} of random {@link Person} from the cache.
     */
    public Future<List<Person>> createTestPeople(int numberToCreate) {
        return getPeopleFuture(numberToCreate, false, PEOPLE_KEY);
    }

    /**
     * Creates a {@link Future} that will return a {@link List} of static {@link Person} with the id populated, from the cache.
     * Each call will return the exact same {@link List} of {@link Person}.
     * @param numberToCreate the number of {@link Person} with the id populated, to create.
     * @return a {@link Future} that will return a {@link List} of random {@link Person} with the id populated, from the cache.
     */
    public Future<List<Person>> createTestPeopleWithId(int numberToCreate) {
        return getPeopleFuture(numberToCreate, false, PEOPLE_WITH_ID_KEY);
    }

    /**
     * @param numberToCreate the number of random {@link Person} to create.
     * @return a {@link Future} that will return a {@link List} of random {@link Person} from the cache.
     */
    public Future<List<Person>> createRandomTestPeople(int numberToCreate) {
        return getPeopleFuture(numberToCreate, true, PEOPLE_KEY);
    }

    /**
     * @param numberToCreate the number of random {@link Person} with the id populated, to create.
     * @return a {@link Future} that will return a {@link List} of random {@link Person} with the id populated, from the cache.
     */
    public Future<List<Person>> createRandomTestPeopleWithId(int numberToCreate) {
        return getPeopleFuture(numberToCreate, true, PEOPLE_WITH_ID_KEY);
    }

    // Caffeine loads on its own executor, so bind the completion back to the calling Vert.x context
    private Future<List<Person>> loadPeople(String peopleKey) {
        return Future.fromCompletionStage(peopleCache.get(peopleKey), vertx.getOrCreateContext());
    }

    private Future<List<Person>> getPeopleFuture(int numberToCreate,
                                                 boolean random,
                                                 String peopleKey) {
        return loadPeople(peopleKey).map(people -> {
            int size = people.size();
            if(!random && size < numberToCreate){
                throw new IllegalArgumentException("Cannot create "+numberToCreate+" people, a max of "+size+" are available." +
                                                           "\nIf you must create more people, use one of the random variants.");
            }

            List<Person> ret = new ArrayList<>(numberToCreate);
            for(int i = 0; i < numberToCreate; i++) {
                ret.add(people.get((random ? KinoticUtil.getRandomNumberInRange(size - 1) : i)));
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
