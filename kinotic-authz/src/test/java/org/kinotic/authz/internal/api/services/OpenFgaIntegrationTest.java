package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.InMemoryEngineStoreRepository;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Exercises the store and relationship services against a real OpenFGA: the system store, created ahead of
 * the servers, found by name on first use, a generated model written once and reused while it is
 * unchanged, relationships written in batches, and checks answering from them. OpenFGA runs from the image the
 * dev stack declares when Docker is available, with its check cache on, else at the URL in the
 * {@code OPENFGA_URL} environment variable, and the test is skipped when there is neither.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenFgaIntegrationTest {

    // the store a deployment creates before a server starts, as compose.openfga.yml and the Helm chart name it
    private static final String SYSTEM_STORE_NAME = "kinotic-system";

    private static GenericContainer<?> openfga;
    private static String apiUrl;

    @Autowired
    private AuthzStoreService storeService;
    @Autowired
    private RelationshipService relationshipService;
    @Autowired
    private AuthzModelGenerator generator;
    @Autowired
    private OpenFgaService fga;
    @Autowired
    private KinoticAuthzProperties properties;
    @Autowired
    private Vertx vertx;
    @Autowired
    private InMemoryEngineStoreRepository engineStores;

    @BeforeAll
    static void startEngine() throws Exception {
        apiUrl = System.getenv("OPENFGA_URL");
        if (apiUrl == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                                   "Docker or OPENFGA_URL is required for the OpenFGA integration test");
            openfga = new GenericContainer<>(openFgaImageFromCompose())
                    // 2112 serves the engine's Prometheus metrics, which count the datastore queries of each check
                    .withExposedPorts(8080, 2112)
                    .withCommand("run")
                    // with no cache controller, a cached check answer lives its whole time to live whatever is
                    // written meanwhile, so a check that skips the cache is told apart from one that reads it
                    .withEnv("OPENFGA_CHECK_QUERY_CACHE_ENABLED", "true")
                    .waitingFor(Wait.forHttp("/healthz").forPort(8080).withStartupTimeout(Duration.ofMinutes(2)));
            openfga.start();
            apiUrl = "http://" + openfga.getHost() + ":" + openfga.getMappedPort(8080);
        }
        createSystemStoreAheadOfTheServers();
    }

    /**
     * The role terraform or the dev stack plays before a server starts: the one store of the system's name.
     */
    private static void createSystemStoreAheadOfTheServers() throws Exception {
        KinoticAuthzProperties properties = new KinoticAuthzProperties();
        properties.getAuthz().setApiUrl(apiUrl);
        OpenFgaService fga = new OpenFgaService(properties);
        boolean exists = await(fga.listStores(100, null, SYSTEM_STORE_NAME))
                .getStores().stream()
                .anyMatch(store -> SYSTEM_STORE_NAME.equals(store.getName()));
        if (!exists) {
            await(fga.createStore(new CreateStoreRequest().name(SYSTEM_STORE_NAME)));
        }
    }

    @DynamicPropertySource
    static void engineUrl(DynamicPropertyRegistry registry) {
        registry.add("kinotic.authz.apiUrl", () -> apiUrl);
    }

    @AfterAll
    static void stopEngine() {
        if (openfga != null) {
            openfga.stop();
        }
    }

    @Test
    void systemModelGoesToTheStoreOfTheSystemsName() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));

        String modelId = await(storeService.ensureModel(SYSTEM, model));

        assertEquals(modelId, await(storeService.ensureModel(SYSTEM, model)));
        // every node resolves the same store
        assertEquals(modelId, await(new DefaultAuthzStoreService(fga, properties, vertx, engineStores).ensureModel(SYSTEM, model)));
        String systemStoreId = await(fga.listStores(100, null, SYSTEM_STORE_NAME))
                .getStores().getFirst().getId();
        assertEquals(modelId, await(fga.readAuthorizationModels(systemStoreId, 1, null))
                .getAuthorizationModels().getFirst().getId());
    }

    @Test
    void anUnreachableEngineFailsTheCallAndTheNodeKeepsRunning() {
        KinoticAuthzProperties unreachable = new KinoticAuthzProperties();
        unreachable.getAuthz().setApiUrl("http://127.0.0.1:1");
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(new OpenFgaService(unreachable), unreachable, vertx, engineStores);
        AuthzModel model = generator.systemModel(List.of(projectService()));

        assertThrows(ExecutionException.class, () -> await(node.ensureModel(SYSTEM, model)));
        assertThrows(ExecutionException.class, () -> await(node.ensureModel(SYSTEM, model)));
    }

    @Test
    void modelIsWrittenOnceAndReusedWhileUnchanged() throws Exception {
        await(storeService.ensureStore("model-test"));
        AuthzModel model = generator.systemModel(List.of(projectService()));

        String modelId = await(storeService.ensureModel("model-test", model));

        assertEquals(modelId, await(storeService.ensureModel("model-test", model)));
        AuthzModel grown = generator.systemModel(List.of(projectService(), vmNodeService()));
        assertNotEquals(modelId, await(storeService.ensureModel("model-test", grown)));
    }

    @Test
    void anApplicationsStoreIsCreatedOnceFoundByNameAndDeletedWithEverythingInIt() throws Exception {
        String store = "lifecycle-" + System.nanoTime();
        AuthzModel model = generator.applicationModel(List.of(), List.of());

        String storeId = await(storeService.ensureStore(store));

        assertEquals(storeId, await(storeService.ensureStore(store)));
        // every node resolves the store the first created, by its name
        assertEquals(storeId, await(new DefaultAuthzStoreService(fga, properties, vertx, engineStores).ensureStore(store)));
        String modelId = await(storeService.ensureModel(store, model));
        assertEquals(modelId, await(storeService.modelId(store)));
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("user:bob", "end_user", "application:" + store))));
        assertTrue(await(relationshipService.holds(store, new RelationshipTuple("user:bob", "end_user", "application:" + store))));

        String name = await(fga.getStore(storeId)).getName();
        // a second store listed under the name goes with the store
        await(fga.createStore(new CreateStoreRequest().name(name)));

        await(storeService.deleteStore(store));

        assertThrows(ExecutionException.class, () -> await(storeService.modelId(store)));
        assertTrue(await(fga.listStores(10, null, name)).getStores().isEmpty());
        // a store already gone leaves nothing to delete
        await(storeService.deleteStore(store));
    }

    @Test
    void nodesCreatingAStoreTogetherCreateOne() throws Exception {
        String store = "together-" + System.nanoTime();
        List<Future<String>> creators = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            creators.add(new DefaultAuthzStoreService(fga, properties, vertx, engineStores).ensureStore(store));
        }

        await(Future.all(creators));

        Set<String> ids = creators.stream().map(Future::result).collect(Collectors.toSet());
        assertEquals(1, ids.size());
        String name = await(fga.getStore(ids.iterator().next())).getName();
        assertEquals(1, await(fga.listStores(10, null, name)).getStores().size());
        await(storeService.deleteStore(store));
    }

    @Test
    void aModelIsWrittenOnlyOnceTheRolesItGrantsThroughAreInStep() throws Exception {
        String store = "roles-first-" + System.nanoTime();
        AuthzModel kernel = generator.applicationModel(List.of(entitiesRepository()), List.of());
        AuthzModel grown = generator.applicationModel(List.of(entitiesRepository(), tenantService()), List.of());
        String storeId = await(storeService.ensureStore(store));
        await(relationshipService.ensureModelWithRoles(store, kernel));

        String grownModelId = await(relationshipService.ensureModelWithRoles(store, grown)).modelId();

        // the version before the grown one carries the role relations of the tenant's permission, and no type
        // grants through them
        List<AuthorizationModel> versions = await(fga.readAuthorizationModels(storeId, 10, null)).getAuthorizationModels();
        assertEquals(grownModelId, versions.getFirst().getId());
        AuthorizationModel bridge = versions.get(1);
        assertTrue(relationsOf(bridge, "tenant_role").contains("tenant_can_read"));
        assertFalse(relationsOf(bridge, "tenant").contains("tenant_can_read"));
        assertTrue(relationsOf(bridge, "tenant").contains("entity_definition_can_read"));
        assertTrue(await(relationshipService.holds(store, new RelationshipTuple("user:*", "tenant_can_read", AuthzUtil.roleObject("application.admin")))));
        // a model in step writes nothing more
        assertEquals(grownModelId, await(relationshipService.ensureModelWithRoles(store, grown)).modelId());
        assertEquals(versions.size(), await(fga.readAuthorizationModels(storeId, 10, null)).getAuthorizationModels().size());
        await(storeService.deleteStore(store));
    }

    @Test
    void aStoreIsCalledByTheIdsItsRecordNamesAndByTheNewestVersionOnceThatOneRefuses() throws Exception {
        String store = "recorded-" + System.nanoTime();
        AuthzModel kernel = generator.applicationModel(List.of(entitiesRepository()), List.of());
        AuthzModel grown = generator.applicationModel(List.of(entitiesRepository(), tenantService()), List.of());
        await(storeService.ensureStore(store));
        EngineStore withKernel = await(relationshipService.ensureModelWithRoles(store, kernel));
        EngineStore withGrown = await(relationshipService.ensureModelWithRoles(store, grown));
        await(relationshipService.bind(store, "tenant.viewer", "user:bob", "tenant:t1"));
        RelationshipTuple reading = new RelationshipTuple("user:bob", "tenant_can_read", "tenant:t1");

        // the engine has no store under this name, so the ids are the record's
        String recorded = "recorded-as-" + System.nanoTime();
        engineStores.save(recorded, withGrown);
        assertEquals(withGrown.modelId(), await(storeService.modelId(recorded)));
        assertTrue(await(relationshipService.check(recorded, reading, Consistency.HIGHER_CONSISTENCY)));

        // a record naming the version before the newest: that version refuses a relation it lacks, and the newest
        // answers from then on
        String behind = "recorded-behind-" + System.nanoTime();
        engineStores.save(behind, withKernel);
        assertEquals(withKernel.modelId(), await(storeService.modelId(behind)));
        assertTrue(await(relationshipService.check(behind, reading, Consistency.HIGHER_CONSISTENCY)));
        assertEquals(withGrown.modelId(), await(storeService.modelId(behind)));
        await(storeService.deleteStore(store));
    }

    @Test
    void aRecordNamingTheStoreDeletedUnderItsNameGivesWayToTheStoreCreatedAgain() throws Exception {
        String store = "recorded-deleted-" + System.nanoTime();
        AuthzModel model = generator.applicationModel(List.of(), List.of());
        // written through another node, so this one first reads the store's record once it names the ids
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties, vertx, engineStores);
        String storeId = await(elsewhere.ensureStore(store));
        String modelId = await(elsewhere.ensureModel(store, model));
        engineStores.save(store, new EngineStore(storeId, modelId));
        assertEquals(modelId, await(storeService.modelId(store)));

        // the record outlives the engine's store, as an application's does while the application is deleted
        await(storeService.deleteStore(store));
        String recreated = await(storeService.ensureStore(store));
        String recreatedModelId = await(storeService.ensureModel(store, model));

        assertNotEquals(storeId, recreated);
        assertNotEquals(modelId, recreatedModelId);
        assertEquals(recreatedModelId, await(storeService.modelId(store)));
        await(storeService.deleteStore(store));
    }

    private static Set<String> relationsOf(AuthorizationModel model, String type) {
        return model.getTypeDefinitions().stream()
                    .filter(definition -> type.equals(definition.getType()))
                    .findFirst()
                    .map(definition -> definition.getRelations().keySet())
                    .orElse(Set.of());
    }

    @Test
    void aStoreCreatedAgainUnderItsNameIsResolvedAgainByEveryNodeWithinTheRetention() throws Exception {
        String store = "recreated-" + System.nanoTime();
        AuthzModel model = generator.applicationModel(List.of(), List.of());
        RelationshipTuple bob = new RelationshipTuple("user:bob", "end_user", "application:" + store);
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties, vertx, engineStores);
        DefaultRelationshipService relationshipsElsewhere = new DefaultRelationshipService(fga, elsewhere, vertx);
        String storeId = await(storeService.ensureStore(store));
        String modelId = await(storeService.ensureModel(store, model));
        await(relationshipService.ensure(store, List.of(bob)));
        // another node resolved the store and read its model before the application was deleted
        assertEquals(storeId, await(elsewhere.ensureStore(store)));
        assertEquals(modelId, await(elsewhere.modelId(store)));
        await(storeService.deleteStore(store));

        String recreated = await(storeService.ensureStore(store));
        AuthzModel grown = generator.applicationModel(List.of(tenantService()), List.of());
        String grownModelId = await(storeService.ensureModel(store, grown));
        await(relationshipService.ensureRoles(store, grown.roles()));
        await(relationshipService.bind(store, "tenant.viewer", "user:bob", "tenant:t1"));

        assertNotEquals(storeId, recreated);
        // the engine keeps answering for the deleted store, so the other node answers from it until the retention
        // has passed and it confirms the id it kept, finds the store gone and resolves the name again
        long deadline = System.currentTimeMillis() + 45_000;
        while (await(relationshipsElsewhere.holds(store, bob)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertFalse(await(relationshipsElsewhere.holds(store, bob)));
        assertEquals(grownModelId, await(elsewhere.modelId(store)));
        assertTrue(await(relationshipsElsewhere.check(store, new RelationshipTuple("user:bob", "tenant_can_read", "tenant:t1"),
                                                      Consistency.HIGHER_CONSISTENCY)));
        await(storeService.deleteStore(store));
    }

    @Test
    void aCheckOnARelationTheKeptVersionLacksIsAnsweredByTheNewest() throws Exception {
        String store = "grown-" + System.nanoTime();
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties, vertx, engineStores);
        DefaultRelationshipService relationshipsElsewhere = new DefaultRelationshipService(fga, elsewhere, vertx);
        await(storeService.ensureStore(store));
        String modelId = await(storeService.ensureModel(store, generator.applicationModel(List.of(), List.of())));
        // another node read the store's version before the model grew
        assertEquals(modelId, await(elsewhere.modelId(store)));

        AuthzModel grown = generator.applicationModel(List.of(tenantService()), List.of());
        await(storeService.ensureModel(store, grown));
        await(relationshipService.ensureRoles(store, grown.roles()));
        await(relationshipService.bind(store, "tenant.viewer", "user:bob", "tenant:t1"));

        assertEquals(modelId, await(elsewhere.modelId(store)));
        assertTrue(await(relationshipsElsewhere.check(store, new RelationshipTuple("user:bob", "tenant_can_read", "tenant:t1"),
                                                      Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of("tenant:t1"), await(relationshipsElsewhere.listObjects(store, "user:bob", "tenant_can_read", "tenant",
                                                                                    Consistency.HIGHER_CONSISTENCY)));
        // a relation no version has is held by no one
        assertFalse(await(relationshipService.check(store, new RelationshipTuple("user:bob", "nothing_can_read", "tenant:t1"),
                                                    Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of(), await(relationshipService.listObjects(store, "user:bob", "nothing_can_read", "tenant",
                                                                      Consistency.HIGHER_CONSISTENCY)));
        await(storeService.deleteStore(store));
    }

    @Test
    void listingReturnsEveryObjectPastTheEnginesListLimit() throws Exception {
        await(relationshipService.ensureModelWithRoles(SYSTEM, generator.systemModel(List.of(projectService()))));
        // the engine's ListObjects stops at 1000 objects by default
        List<RelationshipTuple> written = new ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            written.add(new RelationshipTuple("application:crm-lists", "application", "project:p" + i + "-lists"));
        }
        await(relationshipService.ensure(SYSTEM, written));
        String grantId = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-lists", "application:crm-lists"));

        assertEquals(1100, await(relationshipService.listObjects(SYSTEM, "user:sally-lists", "project_can_read", "project",
                                                                 Consistency.HIGHER_CONSISTENCY)).size());
        await(relationshipService.revoke(SYSTEM, grantId, "application:crm-lists"));
        await(relationshipService.remove(SYSTEM, written));
    }

    @Test
    void aCheckCostsTheSameHoweverManyHoldARoleOnItsPath() throws Exception {
        Assumptions.assumeTrue(openfga != null, "the engine's metrics are read from the container this test starts");
        await(relationshipService.ensureModelWithRoles(SYSTEM, generator.systemModel(List.of(projectService()))));
        List<String> path = List.of("project:billing-cost", "application:crm-cost", "organization:acme-cost");
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple(path.get(2), "organization", path.get(1)),
                                                         new RelationshipTuple(path.get(1), "application", path.get(0)))));
        for (String object : path) {
            await(relationshipService.bind(SYSTEM, "project.editor", "user:first-cost", object));
        }
        // a refusal reads every path to the object, and higher consistency skips the engine's check cache
        RelationshipTuple refused = new RelationshipTuple("user:nobody-cost", "project_can_read", path.get(0));
        double withOne = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, refused, Consistency.HIGHER_CONSISTENCY));

        for (int i = 0; i < 100; i++) {
            for (String object : path) {
                await(relationshipService.bind(SYSTEM, "project.editor", "user:member" + i + "-cost", object));
            }
        }
        double withMany = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, refused, Consistency.HIGHER_CONSISTENCY));

        // a check that opened each grant would read hundreds of times more
        assertTrue(withMany < 2 * withOne,
                   "a refused check took " + withOne + " datastore queries with one member of the role on each object of its path and "
                           + withMany + " with 101");
        for (String object : path) {
            await(relationshipService.removeObject(SYSTEM, object));
        }
    }

    @Test
    void aCheckCostsTheSameHoweverManyRolesAreGrantedOnItsPath() throws Exception {
        Assumptions.assumeTrue(openfga != null, "the engine's metrics are read from the container this test starts");
        await(relationshipService.ensureModelWithRoles(SYSTEM, generator.systemModel(List.of(projectService()))));
        List<String> path = List.of("project:billing-roles-cost", "application:crm-roles-cost", "organization:acme-roles-cost");
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple(path.get(2), "organization", path.get(1)),
                                                         new RelationshipTuple(path.get(1), "application", path.get(0)))));
        List<RelationshipTuple> roles = new ArrayList<>();
        for (int i = 0; i <= 50; i++) {
            roles.add(new RelationshipTuple("user:*", "project_can_edit", "project_role:project.custom" + i + "-roles-cost"));
        }
        await(relationshipService.ensure(SYSTEM, roles));
        for (String object : path) {
            await(relationshipService.bind(SYSTEM, "project.custom0-roles-cost", "user:holder-roles-cost", object));
        }
        // a refusal reads every path to the object, and higher consistency skips the engine's check cache
        RelationshipTuple ofAHolder = new RelationshipTuple("user:holder-roles-cost", "project_can_delete", path.get(0));
        RelationshipTuple ofNoHolder = new RelationshipTuple("user:nobody-roles-cost", "project_can_delete", path.get(0));
        double holderWithOne = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, ofAHolder, Consistency.HIGHER_CONSISTENCY));
        double nobodyWithOne = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, ofNoHolder, Consistency.HIGHER_CONSISTENCY));

        for (int i = 1; i <= 50; i++) {
            for (String object : path) {
                await(relationshipService.bind(SYSTEM, "project.custom" + i + "-roles-cost", "user:member" + i + "-roles-cost", object));
            }
        }
        double holderWithMany = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, ofAHolder, Consistency.HIGHER_CONSISTENCY));
        double nobodyWithMany = datastoreQueriesOf(() -> relationshipService.check(SYSTEM, ofNoHolder, Consistency.HIGHER_CONSISTENCY));

        // a check that opened the binding of each role would read tens of times more
        assertTrue(holderWithMany < 2 * holderWithOne,
                   "a refused check of a holder of one role took " + holderWithOne + " datastore queries with one role granted on each"
                           + " object of its path and " + holderWithMany + " with 51");
        assertTrue(nobodyWithMany < 2 * nobodyWithOne,
                   "a refused check of a holder of no role took " + nobodyWithOne + " datastore queries with one role granted on each"
                           + " object of its path and " + nobodyWithMany + " with 51");
        for (String object : path) {
            await(relationshipService.removeObject(SYSTEM, object));
        }
        await(relationshipService.remove(SYSTEM, roles));
    }

    @Test
    void aGrantMadeAfterARefusalAnswersTheNextCheck() throws Exception {
        await(relationshipService.ensureModelWithRoles(SYSTEM, generator.systemModel(List.of(projectService()))));
        String project = "project:billing-regrant";
        RelationshipTuple editing = new RelationshipTuple("user:sally-regrant", "project_can_edit", project);
        RelationshipTuple reading = new RelationshipTuple("user:sally-regrant", "project_can_read", project);
        String viewing = await(relationshipService.bind(SYSTEM, "project.viewer", "user:sally-regrant", project));
        // the roles the user holds are listed for the first check and kept for the ones after it
        assertTrue(await(relationshipService.check(SYSTEM, reading, Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, editing, Consistency.HIGHER_CONSISTENCY)));

        String granted = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-regrant", project));

        assertTrue(await(relationshipService.check(SYSTEM, editing, Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.revoke(SYSTEM, granted, project));
        assertFalse(await(relationshipService.check(SYSTEM, editing, Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.check(SYSTEM, reading, Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.revoke(SYSTEM, viewing, project));
        // a binding leaves its role's holders with its last member
        for (String roleId : List.of("project.editor", "project.viewer")) {
            assertFalse(await(relationshipService.holds(SYSTEM, new RelationshipTuple(AuthzUtil.bindingObject(project, roleId), "binding",
                                                                                      "role_holders:" + roleId))));
        }
    }

    // The datastore queries the engine counts for one check, averaged over ten
    private double datastoreQueriesOf(Supplier<Future<Boolean>> check) throws Exception {
        double before = checkQueryCount();
        for (int i = 0; i < 10; i++) {
            assertFalse(await(check.get()));
        }
        return (checkQueryCount() - before) / 10;
    }

    // The sum of the engine's histogram of datastore queries per Check request
    private static double checkQueryCount() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + openfga.getHost() + ":" + openfga.getMappedPort(2112) + "/metrics")).build();
        String metrics = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        double ret = 0;
        for (String line : metrics.split("\n")) {
            if (line.startsWith("openfga_datastore_query_count_sum{") && line.toLowerCase().contains("grpc_method=\"check\"")) {
                ret += Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        return ret;
    }

    @Test
    void checksAnswerFromWrittenRelationships() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));
        List<RelationshipTuple> written = List.of(
                new RelationshipTuple("user:sally-checks", "member", "organization:acme-checks"),
                new RelationshipTuple("organization:acme-checks", "organization", "application:crm-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:billing-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:shipping-checks"),
                new RelationshipTuple("user:*", "project_can_edit", "project_role:project.editor_checks"));
        await(relationshipService.ensure(SYSTEM, written));
        String granted = await(relationshipService.bind(SYSTEM, "project.editor_checks", "user:sally-checks", "project:billing-checks"));

        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // editing implies reading
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_read", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_delete", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_read", "project:shipping-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of("project:billing-checks"),
                     await(relationshipService.listObjects(SYSTEM, "user:sally-checks", "project_can_read", "project", Consistency.HIGHER_CONSISTENCY)));
        // the grant of a role bundling only editing explains reading, which editing implies
        Grant editing = new Grant(granted, "project.editor_checks", new Subject(SubjectType.USER, "sally-checks"),
                                  new Resource("project", "billing-checks"));
        assertEquals(List.of(editing), await(relationshipService.explain(SYSTEM, model,
                                                                       new RelationshipTuple("user:sally-checks", "project_can_read", "project:billing-checks"),
                                                                       List.of(), List.of(editing))).through());
        assertEquals(List.of(), await(relationshipService.explain(SYSTEM, model,
                                                                  new RelationshipTuple("user:sally-checks", "project_can_delete", "project:billing-checks"),
                                                                  List.of(), List.of(editing))).through());

        await(relationshipService.revoke(SYSTEM, granted, "project:billing-checks"));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // the store is shared with the other tests and with a run against a persistent engine, so nothing written here stays
        await(relationshipService.remove(SYSTEM, written));
    }

    @Test
    void anExplanationNamesTheGrantsWhoseRolesHoldThePermission() throws Exception {
        String store = "acme.crm-explains";
        await(storeService.ensureStore(store));
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository(), tenantService()), List.of());
        await(relationshipService.ensureModelWithRoles(store, model));
        String tenant = "tenant:north-explains";
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("application:crm-explains", "application", tenant))));
        // a role homed at the definition, below the tenant it is granted on, and one homed at the tenant
        String editing = await(relationshipService.bind(store, "entity_definition.editor", "user:sally-explains", tenant));
        String viewing = await(relationshipService.bind(store, "tenant.viewer", "user:sally-explains", tenant));
        List<Grant> grants = await(relationshipService.findGrants(store, tenant));

        AccessExplanation reads = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:sally-explains", "tenant_can_read", tenant), List.of(), grants));
        AccessExplanation edits = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:sally-explains", "entity_definition_can_edit", tenant), List.of(), grants));

        assertTrue(reads.allowed());
        assertEquals(List.of(viewing), reads.through().stream().map(Grant::id).toList());
        assertTrue(edits.allowed());
        assertEquals(List.of(editing), edits.through().stream().map(Grant::id).toList());
        await(storeService.deleteStore(store));
    }

    @Test
    void anExplanationNamesATenantsGrantForAPermissionTheTenantCarries() throws Exception {
        String store = "acme.crm-carried";
        await(storeService.ensureStore(store));
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository()), List.of());
        await(relationshipService.ensureModelWithRoles(store, model));
        String tenant = "tenant:north-carried";
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("application:crm-carried", "application", tenant))));
        String administering = await(relationshipService.bind(store, "entity_definition.admin", "user:sally-carried", tenant));

        AccessExplanation reads = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:sally-carried", "entity_definition_can_read", tenant), List.of(),
                await(relationshipService.findGrants(store, tenant))));

        assertTrue(reads.allowed());
        assertEquals(List.of(administering), reads.through().stream().map(Grant::id).toList());
        await(storeService.deleteStore(store));
    }

    @Test
    void aRoleHoldsThePermissionsOfTheApplicationsServicesItNames() throws Exception {
        String store = "acme.crm-services";
        await(storeService.ensureStore(store));
        String kernelModelId = await(relationshipService.ensureModelWithRoles(
                store, generator.applicationModel(List.of(entitiesRepository()), List.of()))).modelId();
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository()), List.of(reportService(), chartService()));
        // a service's permissions are named by roles alone, so its registration writes no version of the model
        assertEquals(kernelModelId, await(relationshipService.ensureModelWithRoles(store, model)).modelId());
        String application = "application:crm-services";
        await(relationshipService.ensure(store, List.of(new RelationshipTuple(application, "application", "tenant:north"),
                                                        new RelationshipTuple(application, "application", "tenant:south"))));

        // a role made for the application names the permissions it holds
        RolePermissions analyst = model.bundle(Set.of("report_can_generate", "entity_definition_can_read"));
        assertEquals(new RolePermissions(Set.of("entity_definition_can_read"), Map.of(), Set.of("report_can_generate")), analyst);
        await(relationshipService.ensureRole(store, "tenant.analyst", analyst));
        assertEquals(analyst, await(relationshipService.findRolePermissions(store, "tenant.analyst")));
        String analysing = await(relationshipService.bind(store, "tenant.analyst", "user:bob-services", "tenant:north"));
        assertTrue(holdsOfServices(store, model, "user:bob-services", "report_can_generate", "tenant:north"));
        assertFalse(holdsOfServices(store, model, "user:bob-services", "report_can_read", "tenant:north"));
        assertFalse(holdsOfServices(store, model, "user:bob-services", "chart_can_read", "tenant:north"));
        // the grant was made on one tenant
        assertFalse(holdsOfServices(store, model, "user:bob-services", "report_can_generate", "tenant:south"));
        assertFalse(holdsOfServices(store, model, "user:bob-services", "report_can_generate", application));
        // asked about no permission, none is held
        assertFalse(await(relationshipService.check(store, new RelationshipTuple("user:bob-services", "service_permission", "tenant:north"),
                                                    Consistency.HIGHER_CONSISTENCY)));

        // a built-in role of a service's type holds what the model says and the store holds nothing for it; it is
        // granted as a role of the tenant, and granted on the application it reaches every tenant
        assertEquals(Set.of("report.admin", "report.editor", "report.viewer"), model.rolesHolding("report_can_read"));
        assertTrue(await(relationshipService.read(store, "tenant_role:report.viewer")).isEmpty());
        String viewing = await(relationshipService.bind(store, "report.viewer", "user:sally-services", "tenant:north"));
        await(relationshipService.bind(store, "chart.viewer", "user:machine-services", application));
        assertTrue(holdsOfServices(store, model, "user:sally-services", "report_can_read", "tenant:north"));
        assertFalse(holdsOfServices(store, model, "user:sally-services", "report_can_generate", "tenant:north"));
        assertFalse(holdsOfServices(store, model, "user:sally-services", "report_can_read", "tenant:south"));
        assertTrue(holdsOfServices(store, model, "user:machine-services", "chart_can_read", application));
        assertTrue(holdsOfServices(store, model, "user:machine-services", "chart_can_read", "tenant:south"));
        assertFalse(holdsOfServices(store, model, "user:machine-services", "report_can_read", "tenant:south"));

        // the application's administrator holds every permission of every service, one registered later included
        await(relationshipService.bind(store, "application.admin", "user:ada-services", application));
        assertTrue(holdsOfServices(store, model, "user:ada-services", "report_can_generate", "tenant:south"));
        assertTrue(holdsOfServices(store, model, "user:ada-services", "invoice_can_send", application));

        AccessExplanation generates = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:bob-services", "report_can_generate", "tenant:north"), List.of(),
                await(relationshipService.findGrants(store, "tenant:north"))));
        assertTrue(generates.allowed());
        assertEquals(List.of(analysing), generates.through().stream().map(Grant::id).toList());
        AccessExplanation reads = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:bob-services", "report_can_read", "tenant:north"), List.of(),
                await(relationshipService.findGrants(store, "tenant:north"))));
        assertFalse(reads.allowed());
        assertTrue(reads.through().isEmpty());
        AccessExplanation views = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:sally-services", "report_can_read", "tenant:north"), List.of(),
                await(relationshipService.findGrants(store, "tenant:north"))));
        assertTrue(views.allowed());
        assertEquals(List.of(viewing), views.through().stream().map(Grant::id).toList());

        // an edit takes a permission away and names another
        RolePermissions edited = model.bundle(Set.of("chart_can_read"));
        await(relationshipService.ensureRole(store, "tenant.analyst", edited));
        assertEquals(edited, await(relationshipService.findRolePermissions(store, "tenant.analyst")));
        assertFalse(holdsOfServices(store, model, "user:bob-services", "report_can_generate", "tenant:north"));
        assertTrue(holdsOfServices(store, model, "user:bob-services", "chart_can_read", "tenant:north"));
        await(storeService.deleteStore(store));
    }

    @Test
    void aCheckCostsTheSameHoweverManyServicePermissionsARoleNames() throws Exception {
        Assumptions.assumeTrue(openfga != null, "the engine's metrics are read from the container this test starts");
        String store = "acme.crm-many-services";
        await(storeService.ensureStore(store));
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository()), List.of());
        await(relationshipService.ensureModelWithRoles(store, model));
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("application:crm-many-services", "application", "tenant:north"))));
        int services = 3000;
        Set<String> named = new TreeSet<>();
        for (int i = 0; i < services; i++) {
            named.add("service" + i + "_can_read");
            named.add("service" + i + "_can_edit");
        }
        RolePermissions many = new RolePermissions(Set.of(), Map.of(), named);
        await(relationshipService.ensureRole(store, "tenant.many", many));
        await(relationshipService.ensureRole(store, "tenant.few", new RolePermissions(Set.of(), Map.of(), Set.of("few_can_read"))));
        await(relationshipService.bind(store, "tenant.many", "user:mia-many", "tenant:north"));
        await(relationshipService.bind(store, "tenant.few", "user:fay-many", "tenant:north"));

        assertEquals(many, await(relationshipService.findRolePermissions(store, "tenant.many")));
        assertTrue(await(relationshipService.read(store, "tenant_role:tenant.many", "part")).size() > 1);
        for (int service : List.of(0, services / 2, services - 1)) {
            assertTrue(holdsOfServices(store, model, "user:mia-many", "service" + service + "_can_edit", "tenant:north"));
            assertFalse(holdsOfServices(store, model, "user:mia-many", "service" + service + "_can_delete", "tenant:north"));
        }
        assertTrue(holdsOfServices(store, model, "user:fay-many", "few_can_read", "tenant:north"));

        // a refusal reads every path to the tenant, and the holder of either role is refused a permission it does
        // not name
        double ofFew = datastoreQueriesOf(() -> relationshipService.checkServicePermission(
                store, "user:fay-many", "unnamed_can_read", "tenant:north", Set.of(), Consistency.HIGHER_CONSISTENCY));
        double ofMany = datastoreQueriesOf(() -> relationshipService.checkServicePermission(
                store, "user:mia-many", "unnamed_can_read", "tenant:north", Set.of(), Consistency.HIGHER_CONSISTENCY));
        assertEquals(ofFew, ofMany, 3, "a refused check took " + ofFew + " datastore queries through a role naming one permission and "
                + ofMany + " through one naming " + named.size());
        await(storeService.deleteStore(store));
    }

    private boolean holdsOfServices(String store, AuthzModel model, String user, String permission, String object) throws Exception {
        return await(relationshipService.checkServicePermission(store, user, permission, object, model.rolesHolding(permission),
                                                                Consistency.HIGHER_CONSISTENCY));
    }

    @Test
    void aRoleBundlesAPermissionForTheDefinitionsItNames() throws Exception {
        String store = "acme.crm-named";
        await(storeService.ensureStore(store));
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository()), List.of());
        await(relationshipService.ensureModelWithRoles(store, model));
        String application = "application:crm-named";
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("user:*", "placed", application),
                                                        new RelationshipTuple(application, "application", "tenant:north"),
                                                        new RelationshipTuple(application, "application", "tenant:south"),
                                                        new RelationshipTuple(application, "application", "entity_definition:crm.invoice"),
                                                        new RelationshipTuple(application, "application", "entity_definition:crm.item"),
                                                        new RelationshipTuple(application, "application", "entity_definition:crm.order"))));
        RolePermissions accountant = new RolePermissions(Set.of(), Map.of("crm.invoice", Set.of("entity_definition_can_edit", "entity_definition_can_read"),
                                                                           "crm.item", Set.of("entity_definition_can_read"),
                                                                           "crm.order", Set.of("entity_definition_can_edit")), Set.of());
        await(relationshipService.ensureRole(store, "tenant.accountant", accountant));
        String granted = await(relationshipService.bind(store, "tenant.accountant", "user:bob-named", "tenant:north"));

        assertEquals(accountant, await(relationshipService.findRolePermissions(store, "tenant.accountant")));
        assertTrue(holdsOnRows(store, "user:bob-named", "can_read", "crm.invoice", "north"));
        assertTrue(holdsOnRows(store, "user:bob-named", "can_edit", "crm.invoice", "north"));
        assertFalse(holdsOnRows(store, "user:bob-named", "can_delete", "crm.invoice", "north"));
        assertTrue(holdsOnRows(store, "user:bob-named", "can_read", "crm.item", "north"));
        assertFalse(holdsOnRows(store, "user:bob-named", "can_edit", "crm.item", "north"));
        // each permission stands alone, so a role writes rows it does not read
        assertTrue(holdsOnRows(store, "user:bob-named", "can_edit", "crm.order", "north"));
        assertFalse(holdsOnRows(store, "user:bob-named", "can_read", "crm.order", "north"));
        // the grant was made on one tenant
        assertFalse(holdsOnRows(store, "user:bob-named", "can_read", "crm.invoice", "south"));
        // asked about no definition, a permission bundled for named ones is not held
        assertFalse(await(relationshipService.check(store, new RelationshipTuple("user:bob-named", "entity_definition_can_read", "tenant:north"),
                                                    Consistency.HIGHER_CONSISTENCY)));

        String rows = "tenant_definition:crm.invoice@north";
        AccessExplanation reads = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:bob-named", "entity_definition_can_read", rows),
                edgesOf("crm.invoice", "north"), await(relationshipService.findGrants(store, "tenant:north"))));
        assertTrue(reads.allowed());
        assertEquals(List.of(granted), reads.through().stream().map(Grant::id).toList());
        AccessExplanation deletes = await(relationshipService.explain(
                store, model, new RelationshipTuple("user:bob-named", "entity_definition_can_delete", rows),
                edgesOf("crm.invoice", "north"), await(relationshipService.findGrants(store, "tenant:north"))));
        assertFalse(deletes.allowed());
        assertTrue(deletes.through().isEmpty());

        // an edit names other definitions for a permission, takes one away, and bundles one for every definition
        RolePermissions edited = new RolePermissions(Set.of("entity_definition_can_delete"),
                                                     Map.of("crm.order", Set.of("entity_definition_can_read")), Set.of());
        await(relationshipService.ensureRole(store, "tenant.accountant", edited));
        assertEquals(edited, await(relationshipService.findRolePermissions(store, "tenant.accountant")));
        assertFalse(holdsOnRows(store, "user:bob-named", "can_read", "crm.invoice", "north"));
        assertFalse(holdsOnRows(store, "user:bob-named", "can_edit", "crm.order", "north"));
        assertTrue(holdsOnRows(store, "user:bob-named", "can_read", "crm.order", "north"));
        assertTrue(holdsOnRows(store, "user:bob-named", "can_delete", "crm.item", "north"));
        // in step already: nothing is written
        await(relationshipService.ensureRole(store, "tenant.accountant", edited));
        await(storeService.deleteStore(store));
    }

    @Test
    void aCheckCostsTheSameHoweverManyDefinitionsARoleNames() throws Exception {
        Assumptions.assumeTrue(openfga != null, "the engine's metrics are read from the container this test starts");
        String store = "acme.crm-many";
        await(storeService.ensureStore(store));
        await(relationshipService.ensureModelWithRoles(store, generator.applicationModel(List.of(entitiesRepository()), List.of())));
        String application = "application:crm-many";
        int definitions = 5000;
        List<String> checked = List.of(definitionOf(0), definitionOf(definitions / 2), definitionOf(definitions - 1), "crm.unnamed", "crm.few");
        List<RelationshipTuple> placed = new ArrayList<>(List.of(new RelationshipTuple("user:*", "placed", application),
                                                                 new RelationshipTuple(application, "application", "tenant:north")));
        checked.forEach(definition -> placed.add(new RelationshipTuple(application, "application", "entity_definition:" + definition)));
        await(relationshipService.ensure(store, placed));

        Map<String, Set<String>> named = new HashMap<>();
        for (int i = 0; i < definitions; i++) {
            // every definition is read and every other one edited, so the permissions name different definitions
            named.put(definitionOf(i), i % 2 == 0 ? Set.of("entity_definition_can_read", "entity_definition_can_edit") : Set.of("entity_definition_can_read"));
        }
        RolePermissions many = new RolePermissions(Set.of(), named, Set.of());
        await(relationshipService.ensureRole(store, "tenant.many", many));
        await(relationshipService.ensureRole(store, "tenant.few", new RolePermissions(Set.of(), Map.of("crm.few", Set.of("entity_definition_can_read")), Set.of())));
        String granted = await(relationshipService.bind(store, "tenant.many", "user:mia-many", "tenant:north"));
        await(relationshipService.bind(store, "tenant.few", "user:fay-many", "tenant:north"));

        assertEquals(many, await(relationshipService.findRolePermissions(store, "tenant.many")));
        for (String definition : checked.subList(0, 3)) {
            assertTrue(holdsOnRows(store, "user:mia-many", "can_read", definition, "north"), definition);
            assertFalse(holdsOnRows(store, "user:mia-many", "can_delete", definition, "north"), definition);
        }
        assertTrue(holdsOnRows(store, "user:mia-many", "can_edit", definitionOf(0), "north"));
        assertFalse(holdsOnRows(store, "user:mia-many", "can_edit", definitionOf(definitions - 1), "north"));
        assertFalse(holdsOnRows(store, "user:mia-many", "can_read", "crm.few", "north"));
        assertTrue(holdsOnRows(store, "user:fay-many", "can_read", "crm.few", "north"));

        // a refusal reads every path to the rows, and the holder of either role is refused a definition it does not name
        double ofFew = datastoreQueriesOf(() -> relationshipService.check(
                store, new RelationshipTuple("user:fay-many", "entity_definition_can_read", "tenant_definition:crm.unnamed@north"),
                Consistency.HIGHER_CONSISTENCY, edgesOf("crm.unnamed", "north")));
        double ofMany = datastoreQueriesOf(() -> relationshipService.check(
                store, new RelationshipTuple("user:mia-many", "entity_definition_can_read", "tenant_definition:crm.unnamed@north"),
                Consistency.HIGHER_CONSISTENCY, edgesOf("crm.unnamed", "north")));
        // the engine answers a branch it need not finish in a different number of reads from one check to the next;
        // a check that read every part of the role would read ten more
        assertEquals(ofFew, ofMany, 3, "a refused check took " + ofFew + " datastore queries through a role naming one definition and "
                + ofMany + " through one naming " + definitions);

        // an edit takes one definition away and leaves the rest
        named.remove(definitionOf(0));
        RolePermissions edited = new RolePermissions(Set.of(), named, Set.of());
        await(relationshipService.ensureRole(store, "tenant.many", edited));
        assertEquals(edited, await(relationshipService.findRolePermissions(store, "tenant.many")));
        assertFalse(holdsOnRows(store, "user:mia-many", "can_read", definitionOf(0), "north"));
        assertTrue(holdsOnRows(store, "user:mia-many", "can_read", definitionOf(definitions - 1), "north"));

        // the role's parts leave the store with it
        List<String> parts = await(relationshipService.read(store, "tenant_role:tenant.many", "part")).stream().map(RelationshipTuple::user).toList();
        assertTrue(parts.size() > 1, "the role named its definitions in " + parts.size() + " part");
        await(relationshipService.revoke(store, granted, "tenant:north"));
        await(relationshipService.removeObject(store, "tenant_role:tenant.many"));
        assertEquals(new RolePermissions(Set.of()), await(relationshipService.findRolePermissions(store, "tenant.many")));
        for (String part : parts) {
            assertEquals(List.of(), await(relationshipService.read(store, part)), part);
        }
        await(storeService.deleteStore(store));
    }

    private static String definitionOf(int index) {
        return "crm.entity" + index;
    }

    private boolean holdsOnRows(String store, String user, String permission, String definition, String tenant) throws Exception {
        return await(relationshipService.check(store,
                                               new RelationshipTuple(user, "entity_definition_" + permission, "tenant_definition:" + definition + "@" + tenant),
                                               Consistency.HIGHER_CONSISTENCY, edgesOf(definition, tenant)));
    }

    private static List<RelationshipTuple> edgesOf(String definition, String tenant) {
        String rows = "tenant_definition:" + definition + "@" + tenant;
        return List.of(new RelationshipTuple("entity_definition:" + definition, "definition", rows),
                       new RelationshipTuple("tenant:" + tenant, "tenant", rows));
    }

    @Test
    void theRowsGrantedOnAreRemovedWithTheirDefinitionOrTheirTenant() throws Exception {
        String store = "acme.crm-pairs";
        await(storeService.ensureStore(store));
        await(relationshipService.ensureModelWithRoles(store, generator.applicationModel(List.of(entitiesRepository()), List.of())));
        // the store holds the edges of the rows granted on, which a check is given as well
        for (List<String> rows : List.of(List.of("crm.invoice", "north"), List.of("crm.invoice", "south"), List.of("crm.order", "north"))) {
            await(relationshipService.ensure(store, edgesOf(rows.get(0), rows.get(1))));
            await(relationshipService.bind(store, "entity_definition.viewer", "user:bob-pairs",
                                           "tenant_definition:" + rows.get(0) + "@" + rows.get(1)));
        }
        assertTrue(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.invoice", "north"));
        assertTrue(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.invoice", "south"));

        await(relationshipService.removeObjectsOf(store, "entity_definition:crm.invoice", "tenant_definition"));

        assertFalse(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.invoice", "north"));
        assertFalse(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.invoice", "south"));
        assertEquals(List.of(), await(relationshipService.read(store, "tenant_definition:crm.invoice@north")));
        assertEquals(List.of(), await(relationshipService.read(store, "tenant_definition:crm.invoice@south")));
        assertTrue(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.order", "north"));

        await(relationshipService.removeObjectsOf(store, "tenant:north", "tenant_definition"));

        assertFalse(holdsOnRows(store, "user:bob-pairs", "can_read", "crm.order", "north"));
        assertEquals(List.of(), await(relationshipService.read(store, "tenant_definition:crm.order@north")));
        await(storeService.deleteStore(store));
    }

    @Test
    void theGrantsOfAResourceAreAllFoundHoweverManyItHas() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(relationshipService.ensureModelWithRoles(SYSTEM, model));
        String project = "project:billing-many";
        Set<String> made = new HashSet<>();
        // more members than one page of a read holds, all of them in the role's one binding on the project
        for (int i = 0; i < 150; i++) {
            made.add(await(relationshipService.bind(SYSTEM, "project.editor", "user:member" + i + "-many", project)));
        }

        List<Grant> grants = await(relationshipService.findGrants(SYSTEM, project));

        assertEquals(made, grants.stream().map(Grant::id).collect(Collectors.toSet()));
        assertEquals(150, grants.stream().map(Grant::subject).distinct().count());
        assertEquals(1, await(relationshipService.read(SYSTEM, project, "role_binding")).size());
        await(relationshipService.removeObject(SYSTEM, project));
        assertEquals(List.of(), await(relationshipService.findGrants(SYSTEM, project)));
    }

    @Test
    void builtInRolesAreSeededByDifferenceAndBindingsGrantThem() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));

        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        List<RelationshipTuple> editor = await(relationshipService.read(SYSTEM, AuthzUtil.roleObject("project.editor")));
        assertEquals(Set.of("project_can_edit", "project_can_read"),
                     editor.stream().map(RelationshipTuple::relation).collect(Collectors.toSet()));
        // in step already: nothing is written, and nothing fails on a tuple held already
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        // a permission the role no longer bundles is removed, one it lacks is added
        await(relationshipService.ensureRoles(SYSTEM, Map.of("project.editor", Set.of("project_can_read", "project_can_delete"))));
        assertEquals(Set.of("project_can_delete", "project_can_read"),
                     await(relationshipService.read(SYSTEM, AuthzUtil.roleObject("project.editor"))).stream().map(RelationshipTuple::relation).collect(Collectors.toSet()));
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));

        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("application:crm-roles", "application", "project:billing-roles"))));
        await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-roles", "project:billing-roles"));
        assertTrue(await(relationshipService.holds(SYSTEM, new RelationshipTuple(AuthzUtil.bindingObject("project:billing-roles", "project.editor"), "role_binding", "project:billing-roles"))));
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-roles", "project_can_edit", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-roles", "project_can_delete", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
        // a binding of the organization admin on the organization reaches the project inside it
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("organization:acme-roles", "organization", "application:crm-roles"))));
        await(relationshipService.bind(SYSTEM, AuthzConstants.ORGANIZATION_ADMIN_ROLE, "user:marcus-roles", "organization:acme-roles"));
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:marcus-roles", "project_can_delete", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
        // the engine refuses a role homed above the resource, and a binding attached to a resource of another type
        assertThrows(ExecutionException.class,
                     () -> await(relationshipService.bind(SYSTEM, AuthzConstants.ORGANIZATION_ADMIN_ROLE, "user:marcus-roles", "project:billing-roles")));
        assertThrows(ExecutionException.class, () -> await(relationshipService.ensure(SYSTEM, List.of(
                new RelationshipTuple("organization_role_binding:misplaced-roles", "role_binding", "project:billing-roles")))));
    }

    @Test
    void ensureAndRemoveAreIdempotent() throws Exception {
        await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));
        RelationshipTuple contained = new RelationshipTuple("application:crm-ensure", "application", "project:billing-ensure");
        RelationshipTuple member = new RelationshipTuple("user:sally-ensure", "member", "organization:acme-ensure");

        await(relationshipService.ensure(SYSTEM, List.of(contained, member)));
        await(relationshipService.ensure(SYSTEM, List.of(contained, member)));
        assertTrue(await(relationshipService.holds(SYSTEM, contained)));
        assertTrue(await(relationshipService.holds(SYSTEM, member)));
        await(relationshipService.remove(SYSTEM, List.of(contained)));
        await(relationshipService.remove(SYSTEM, List.of(contained, member)));
        assertFalse(await(relationshipService.holds(SYSTEM, contained)));
        assertFalse(await(relationshipService.holds(SYSTEM, member)));
        // two writers at once, as two servers provisioning one application are, each find the other's work done
        await(Future.all(relationshipService.ensure(SYSTEM, List.of(contained)), relationshipService.ensure(SYSTEM, List.of(contained))));
        assertTrue(await(relationshipService.holds(SYSTEM, contained)));
        await(Future.all(relationshipService.remove(SYSTEM, List.of(contained)), relationshipService.remove(SYSTEM, List.of(contained))));
        assertFalse(await(relationshipService.holds(SYSTEM, contained)));
    }

    @Test
    void aConsistentCheckSeesARevokeACachedAnswerPredates() throws Exception {
        Assumptions.assumeTrue(openfga != null, "Only the engine this test starts is known to cache checks");
        await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));
        List<RelationshipTuple> written = List.of(new RelationshipTuple("user:*", "project_can_edit", "project_role:project.editor_cached"));
        await(relationshipService.ensure(SYSTEM, written));
        String granted = await(relationshipService.bind(SYSTEM, "project.editor_cached", "user:sally-cached", "project:billing-cached"));
        RelationshipTuple editing = new RelationshipTuple("user:sally-cached", "project_can_edit", "project:billing-cached");
        assertTrue(await(relationshipService.check(SYSTEM, editing, Consistency.MINIMIZE_LATENCY)));

        await(relationshipService.revoke(SYSTEM, granted, "project:billing-cached"));

        // the cached allow outlives the revoke, which shows the cache answered; the consistent check does not read it
        assertTrue(await(relationshipService.check(SYSTEM, editing, Consistency.MINIMIZE_LATENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, editing, Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.remove(SYSTEM, written));
    }

    @Test
    void writesBeyondOneRequestAreBatched() throws Exception {
        await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));
        List<RelationshipTuple> members = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            members.add(new RelationshipTuple("user:member" + i, "member", "group:everyone-batch"));
        }

        await(relationshipService.ensure(SYSTEM, members));

        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:member249", "member", "group:everyone-batch"), Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.remove(SYSTEM, members));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:member249", "member", "group:everyone-batch"), Consistency.HIGHER_CONSISTENCY)));
    }

    @Test
    void systemModelIdIsTheVersionTheStoreRuns() throws Exception {
        String modelId = await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));

        assertEquals(modelId, await(storeService.modelId(SYSTEM)));
    }

    @Test
    void revokeTakesOneMemberOutAndTheBindingGoesWithItsLast() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:billing-unbind"))));
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:payroll-unbind"))));
        String sallys = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-unbind", "project:billing-unbind"));
        String marcuss = await(relationshipService.bind(SYSTEM, "project.editor", "user:marcus-unbind", "project:billing-unbind"));
        // a grant made again is the same grant, held once
        assertEquals(sallys, await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-unbind", "project:billing-unbind")));
        assertEquals(2, await(relationshipService.findGrants(SYSTEM, "project:billing-unbind")).size());
        String binding = AuthzUtil.bindingObject("project:billing-unbind", "project.editor");
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.readByUser(SYSTEM, AuthzUtil.roleObject("project.editor"), "project_role_binding")).stream()
                        .anyMatch(tuple -> tuple.object().equals(binding)));
        // a grant is revoked where it was made: the same role granted to the same member elsewhere is another grant
        assertThrows(ExecutionException.class, () -> await(relationshipService.revoke(SYSTEM, sallys, "project:payroll-unbind")));

        await(relationshipService.revoke(SYSTEM, sallys, "project:billing-unbind"));

        // the other member of the binding still holds the role
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:marcus-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of(marcuss), await(relationshipService.findGrants(SYSTEM, "project:billing-unbind")).stream().map(Grant::id).toList());
        // a grant already revoked is no grant made on the object
        assertThrows(ExecutionException.class, () -> await(relationshipService.revoke(SYSTEM, sallys, "project:billing-unbind")));

        await(relationshipService.revoke(SYSTEM, marcuss, "project:billing-unbind"));

        // the binding leaves the store with its last member
        assertTrue(await(relationshipService.read(SYSTEM, binding)).isEmpty());
        assertFalse(await(relationshipService.holds(SYSTEM, new RelationshipTuple(binding, "role_binding", "project:billing-unbind"))));
        // a member joining afterwards attaches the binding again
        await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-unbind", "project:billing-unbind"));
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.removeObject(SYSTEM, "project:billing-unbind"));
        await(relationshipService.removeObject(SYSTEM, "project:payroll-unbind"));
    }

    @Test
    void aReadByRelationAndTheGrantsOfAnObjectLeaveOutItsOtherRelationships() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        RelationshipTuple contained = new RelationshipTuple("organization:acme-relation", "organization", "application:crm-relation");
        List<RelationshipTuple> endUsers = new ArrayList<>();
        // more end users than one page of a read holds
        for (int i = 0; i < 150; i++) {
            endUsers.add(new RelationshipTuple("user:end-user-relation" + i, "end_user", "application:crm-relation"));
        }
        await(relationshipService.ensure(SYSTEM, List.of(contained)));
        await(relationshipService.ensure(SYSTEM, endUsers));
        String grantId = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-relation", "application:crm-relation"));

        assertEquals(List.of(contained), await(relationshipService.read(SYSTEM, "application:crm-relation", "organization")));
        assertEquals(150, await(relationshipService.read(SYSTEM, "application:crm-relation", "end_user")).size());
        assertEquals(List.of(new Grant(grantId, "project.editor", new Subject(SubjectType.USER, "sally-relation"), new Resource("application", "crm-relation"))),
                     await(relationshipService.findGrants(SYSTEM, "application:crm-relation")));

        await(relationshipService.removeObject(SYSTEM, "application:crm-relation"));
    }

    @Test
    void aRemovedObjectAndARemovedSubjectLeaveNothingOfThemselves() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("application:crm-gone", "application", "project:billing-gone"),
                                                         new RelationshipTuple("application:crm-gone", "application", "project:payroll-gone"),
                                                         new RelationshipTuple("user:sally-gone", "member", "group:devs-gone"))));
        await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-gone", "project:billing-gone"));
        String kept = await(relationshipService.bind(SYSTEM, "project.editor", "user:marcus-gone", "project:billing-gone"));
        await(relationshipService.bind(SYSTEM, "project.editor", "group:devs-gone#member", "project:payroll-gone"));

        // the subject leaves its groups and its grants, and the grants of others stay
        await(relationshipService.removeSubject(SYSTEM, "user:sally-gone"));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-gone", "project_can_edit", "project:billing-gone"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-gone", "project_can_edit", "project:payroll-gone"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.readByUser(SYSTEM, "user:sally-gone", "project_role_binding")).isEmpty());
        assertTrue(await(relationshipService.findGrants(SYSTEM, "project:billing-gone")).stream().allMatch(grant -> grant.id().equals(kept)));
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:marcus-gone", "project_can_edit", "project:billing-gone"), Consistency.HIGHER_CONSISTENCY)));

        // the object loses its place and every grant made on it
        await(relationshipService.removeObject(SYSTEM, "project:billing-gone"));
        assertTrue(await(relationshipService.read(SYSTEM, "project:billing-gone")).isEmpty());
        assertTrue(await(relationshipService.read(SYSTEM, AuthzUtil.bindingObject("project:billing-gone", "project.editor"))).isEmpty());
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:marcus-gone", "project_can_edit", "project:billing-gone"), Consistency.HIGHER_CONSISTENCY)));

        // a store that does not exist holds nothing of anyone
        await(relationshipService.removeSubject("application:acme.no-such-application-gone", "user:sally-gone"));
        await(relationshipService.removeObject(SYSTEM, "project:payroll-gone"));
    }

    @Test
    void aRemovedSubjectLeavesTheGrantsOfATypeTheModelDropped() throws Exception {
        String store = "dropped-type-" + System.nanoTime();
        AuthzModel withDefinitions = generator.applicationModel(List.of(entitiesRepository()), List.of());
        String definition = "entity_definition:crm.invoice";
        await(storeService.ensureStore(store));
        await(relationshipService.ensureModelWithRoles(store, withDefinitions));
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("application:crm", "application", definition))));
        await(relationshipService.bind(store, "entity_definition.viewer", "user:sally", definition));
        await(relationshipService.bind(store, "entity_definition.viewer", "user:marcus", definition));

        // the system's contracts are published again without the definitions, and sally is removed while the
        // model has no such type
        await(relationshipService.ensureModelWithRoles(store, generator.applicationModel(List.of(), List.of())));
        await(relationshipService.removeSubject(store, "user:sally"));
        await(relationshipService.ensureModelWithRoles(store, withDefinitions));

        assertFalse(await(relationshipService.check(store, new RelationshipTuple("user:sally", "entity_definition_can_read", definition), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.readByUser(store, "user:sally", "entity_definition_role_binding")).isEmpty());
        assertTrue(await(relationshipService.check(store, new RelationshipTuple("user:marcus", "entity_definition_can_read", definition), Consistency.HIGHER_CONSISTENCY)));
        await(storeService.deleteStore(store));
    }

    @Test
    void theStoreResolvedOnOneContextKeepsLaterCallersOnTheirOwn() throws Exception {
        // the store is resolved once and kept, so every caller after the first composes on a kept result
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(fga, properties, vertx, engineStores);
        await(onContext(vertx.getOrCreateContext(), () -> node.modelId(SYSTEM)));
        Context later = vertx.getOrCreateContext();

        boolean stayed = await(onContext(later, () -> node.modelId(SYSTEM).map(id -> Vertx.currentContext() == later)));
        assertTrue(stayed, "a continuation composed on the kept result left the caller's context");
    }

    private static <T> Future<T> onContext(Context context, Supplier<Future<T>> call) {
        Promise<T> promise = Promise.promise();
        context.runOnContext(v -> call.get().onComplete(promise));
        return promise.future();
    }

    @Test
    void anUnknownStoreNameFailsTheCall() {
        assertThrows(ExecutionException.class,
                     () -> await(relationshipService.holds("nowhere", new RelationshipTuple("user:sally", "member", "organization:acme"))));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private static ServiceDefinition projectService() {
        return service("ProjectService", "project", "application",
                       function("findById", "project", "project", "can_read"),
                       function("save", "project", "project", "can_edit"),
                       function("deleteById", "project", "project", "can_delete"));
    }

    // an application's own services, whose permissions its model names in no relation of their own
    private static ServiceDefinition reportService() {
        return service("ReportService", "report", "tenant",
                       function("findById", "report", "report", "can_read"),
                       function("generate", "report", "report", "can_generate"));
    }

    private static ServiceDefinition chartService() {
        return service("ChartService", "chart", "application",
                       function("findById", "chart", "chart", "can_read"));
    }

    // a system service declared on the tenant, whose permission an application's model carries as a relation
    private static ServiceDefinition tenantService() {
        return service("TenantService", "tenant", "application",
                       function("findById", "tenant", "tenant", "can_read"));
    }

    // the system's entities repository, whose declaration gives a definition's rows their permissions
    private static ServiceDefinition entitiesRepository() {
        return service("JsonEntitiesRepository", "entity_definition", "application",
                       function("findById", "entity_definition", "entity_definition", "can_read"),
                       function("save", "entity_definition", "entity_definition", "can_edit"),
                       function("deleteById", "entity_definition", "entity_definition", "can_delete"));
    }

    private static ServiceDefinition vmNodeService() {
        return service("VmNodeService", "vm_node", "system",
                       function("register", "system", "vm_node", "can_register_node"));
    }

    private static ServiceDefinition service(String name, String type, String parent, FunctionDefinition... functions) {
        ServiceDefinition ret = new ServiceDefinition().setNamespace("org.kinotic.test").setName(name);
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(type).setParent(parent)));
        for (FunctionDefinition function : functions) {
            ret.addFunction(function);
        }
        return ret;
    }

    private static FunctionDefinition function(String name, String resource, String permissionResource, String permission) {
        FunctionDefinition ret = new FunctionDefinition().setName(name);
        ret.setDecorators(List.of(new AuthzCheckC3Decorator()
                                          .setResource(resource)
                                          .setResourceId("{id}")
                                          .setPermissionResource(permissionResource)
                                          .setPermission(permission)));
        return ret;
    }

    private static DockerImageName openFgaImageFromCompose() {
        Path compose = Path.of("../deployment/docker-compose/compose.openfga.yml");
        try {
            Matcher matcher = Pattern.compile("image:\\s*(openfga/openfga:[\\w.-]+)").matcher(Files.readString(compose));
            if (!matcher.find()) {
                throw new IllegalStateException("No openfga/openfga image declared in " + compose);
            }
            return DockerImageName.parse(matcher.group(1));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + compose, e);
        }
    }

}
