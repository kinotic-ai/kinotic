package org.kinotic.authz.internal.api.services;

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
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.authz.api.model.EntityScope;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * Exercises the store and relationship services against a real OpenFGA: the platform store, created ahead of
 * the servers, found by name on first use, a generated model written once and reused while it is
 * unchanged, relationships written in batches, and checks answering from them. OpenFGA runs from the image the
 * dev stack declares when Docker is available, else at the URL in the {@code OPENFGA_URL} environment
 * variable, and the test is skipped when there is neither.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenFgaIntegrationTest {

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

    @BeforeAll
    static void startEngine() throws Exception {
        apiUrl = System.getenv("OPENFGA_URL");
        if (apiUrl == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                                   "Docker or OPENFGA_URL is required for the OpenFGA integration test");
            openfga = new GenericContainer<>(openFgaImageFromCompose())
                    .withExposedPorts(8080)
                    .withCommand("run")
                    .waitingFor(Wait.forHttp("/healthz").forPort(8080).withStartupTimeout(Duration.ofMinutes(2)));
            openfga.start();
            apiUrl = "http://" + openfga.getHost() + ":" + openfga.getMappedPort(8080);
        }
        createPlatformStoreAheadOfTheServers();
    }

    /**
     * The role terraform or the dev stack plays before a server starts: the one store of the platform's name.
     */
    private static void createPlatformStoreAheadOfTheServers() throws Exception {
        KinoticAuthzProperties properties = new KinoticAuthzProperties();
        properties.getAuthz().setApiUrl(apiUrl);
        OpenFgaService fga = new OpenFgaService(properties);
        boolean exists = await(fga.listStores(100, null, DefaultAuthzStoreService.PLATFORM_STORE_NAME))
                .getStores().stream()
                .anyMatch(store -> DefaultAuthzStoreService.PLATFORM_STORE_NAME.equals(store.getName()));
        if (!exists) {
            await(fga.createStore(new CreateStoreRequest().name(DefaultAuthzStoreService.PLATFORM_STORE_NAME)));
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
    void platformModelGoesToTheStoreOfThePlatformsName() throws Exception {
        AuthzModel model = generator.platformModel(List.of(projectService()));

        String modelId = await(storeService.ensureModel(PLATFORM, model));

        assertEquals(modelId, await(storeService.ensureModel(PLATFORM, model)));
        // every node resolves the same store
        assertEquals(modelId, await(new DefaultAuthzStoreService(fga, properties).ensureModel(PLATFORM, model)));
        String platformStoreId = await(fga.listStores(100, null, DefaultAuthzStoreService.PLATFORM_STORE_NAME))
                .getStores().getFirst().getId();
        assertEquals(modelId, await(fga.readAuthorizationModels(platformStoreId, 1, null))
                .getAuthorizationModels().getFirst().getId());
    }

    @Test
    void anUnreachableEngineFailsTheCallAndTheNodeKeepsRunning() {
        KinoticAuthzProperties unreachable = new KinoticAuthzProperties();
        unreachable.getAuthz().setApiUrl("http://127.0.0.1:1");
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(new OpenFgaService(unreachable), unreachable);
        AuthzModel model = generator.platformModel(List.of(projectService()));

        assertThrows(ExecutionException.class, () -> await(node.ensureModel(PLATFORM, model)));
        assertThrows(ExecutionException.class, () -> await(node.ensureModel(PLATFORM, model)));
    }

    @Test
    void modelIsWrittenOnceAndReusedWhileUnchanged() throws Exception {
        await(storeService.ensureStore("model-test"));
        AuthzModel model = generator.platformModel(List.of(projectService()));

        String modelId = await(storeService.ensureModel("model-test", model));

        assertEquals(modelId, await(storeService.ensureModel("model-test", model)));
        AuthzModel grown = generator.platformModel(List.of(projectService(), vmNodeService()));
        assertNotEquals(modelId, await(storeService.ensureModel("model-test", grown)));
    }

    @Test
    void anApplicationsStoreIsCreatedOnceFoundByNameAndDeletedWithEverythingInIt() throws Exception {
        String store = "lifecycle-" + System.nanoTime();
        AuthzModel model = generator.applicationModel(List.of(), List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT)));

        String storeId = await(storeService.ensureStore(store));

        assertEquals(storeId, await(storeService.ensureStore(store)));
        // every node resolves the store the first created, by its name
        assertEquals(storeId, await(new DefaultAuthzStoreService(fga, properties).ensureStore(store)));
        String modelId = await(storeService.ensureModel(store, model));
        assertEquals(modelId, await(storeService.modelId(store)));
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("user:bob", "end_user", "application:" + store))));
        assertTrue(await(relationshipService.holds(store, new RelationshipTuple("user:bob", "end_user", "application:" + store))));

        await(storeService.deleteStore(store));

        assertThrows(ExecutionException.class, () -> await(storeService.modelId(store)));
        assertTrue(await(fga.listStores(1, null, DefaultAuthzStoreService.APPLICATION_STORE_PREFIX + store)).getStores().isEmpty());
        // a store already gone leaves nothing to delete
        await(storeService.deleteStore(store));
    }

    @Test
    void aStoreCreatedAgainUnderItsNameIsResolvedAgainByEveryNodeWithinTheRetention() throws Exception {
        String store = "recreated-" + System.nanoTime();
        AuthzModel model = generator.applicationModel(List.of(), List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT)));
        RelationshipTuple bob = new RelationshipTuple("user:bob", "end_user", "application:" + store);
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties);
        DefaultRelationshipService relationshipsElsewhere = new DefaultRelationshipService(fga, elsewhere);
        String storeId = await(storeService.ensureStore(store));
        String modelId = await(storeService.ensureModel(store, model));
        await(relationshipService.ensure(store, List.of(bob)));
        // another node resolved the store and read its model before the application was deleted
        assertEquals(storeId, await(elsewhere.ensureStore(store)));
        assertEquals(modelId, await(elsewhere.modelId(store)));
        await(storeService.deleteStore(store));

        String recreated = await(storeService.ensureStore(store));
        AuthzModel grown = generator.applicationModel(List.of(), List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT),
                                                                         new EntityResource("receipt", EntityScope.TENANT)));
        String grownModelId = await(storeService.ensureModel(store, grown));
        await(relationshipService.ensureRoles(store, grown.roles()));
        await(relationshipService.bind(store, "receipt.viewer", "user:bob", "tenant:t1"));

        assertNotEquals(storeId, recreated);
        // the engine keeps answering for the deleted store, so the other node answers from it until the retention
        // has passed and it confirms the id it kept, finds the store gone and resolves the name again
        long deadline = System.currentTimeMillis() + DefaultAuthzStoreService.RETENTION_MILLIS + 15_000;
        while (await(relationshipsElsewhere.holds(store, bob)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertFalse(await(relationshipsElsewhere.holds(store, bob)));
        assertEquals(grownModelId, await(elsewhere.modelId(store)));
        // a check naming the version the node read before is answered by the newest, as is one naming a relation
        // the version it kept lacked
        RelationshipTuple reads = new RelationshipTuple("user:bob", "receipt_can_read", "tenant:t1");
        assertTrue(await(relationshipsElsewhere.check(store, modelId, reads, Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.check(store, await(storeService.modelId(store)), reads, Consistency.HIGHER_CONSISTENCY)));
        // a relation no version has fails as the engine refuses it
        assertThrows(ExecutionException.class,
                     () -> await(relationshipService.check(store, grownModelId, new RelationshipTuple("user:bob", "nothing_can_read", "tenant:t1"),
                                                           Consistency.HIGHER_CONSISTENCY)));
        await(storeService.deleteStore(store));
    }

    @Test
    void checksAnswerFromWrittenRelationships() throws Exception {
        String modelId = await(storeService.ensureModel(PLATFORM, generator.platformModel(List.of(projectService()))));
        List<RelationshipTuple> written = List.of(
                new RelationshipTuple("user:sally-checks", "member", "organization:acme-checks"),
                new RelationshipTuple("organization:acme-checks", "organization", "application:crm-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:billing-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:shipping-checks"),
                new RelationshipTuple("user:*", "project_can_edit", "role:project_editor_checks"),
                new RelationshipTuple("role:project_editor_checks", "role", "role_binding:sally_edits_billing_checks"),
                new RelationshipTuple("user:sally-checks", "member", "role_binding:sally_edits_billing_checks"),
                new RelationshipTuple("role_binding:sally_edits_billing_checks", "role_binding", "project:billing-checks"));
        await(relationshipService.write(PLATFORM, written, List.of()));

        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // editing implies viewing
        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-checks", "project_can_view", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-checks", "project_can_delete", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-checks", "project_can_view", "project:shipping-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of("project:billing-checks"),
                     await(relationshipService.listObjects(PLATFORM, modelId, "user:sally-checks", "project_can_view", "project", Consistency.HIGHER_CONSISTENCY)));

        await(relationshipService.write(PLATFORM, List.of(), List.of(
                new RelationshipTuple("user:sally-checks", "member", "role_binding:sally_edits_billing_checks"))));
        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // the store is shared with the other tests and with a run against a persistent engine, so nothing written here stays
        await(relationshipService.remove(PLATFORM, written));
    }

    @Test
    void builtInRolesAreSeededByDifferenceAndBindingsGrantThem() throws Exception {
        AuthzModel model = generator.platformModel(List.of(projectService()));
        String modelId = await(storeService.ensureModel(PLATFORM, model));

        await(relationshipService.ensureRoles(PLATFORM, model.roles()));
        List<RelationshipTuple> editor = await(relationshipService.read(PLATFORM, "role:project.editor"));
        assertEquals(Set.of("project_can_edit", "project_can_view"),
                     editor.stream().map(RelationshipTuple::relation).collect(Collectors.toSet()));
        // in step already: nothing is written, and nothing fails on a tuple held already
        await(relationshipService.ensureRoles(PLATFORM, model.roles()));
        // a permission the role no longer bundles is removed, one it lacks is added
        await(relationshipService.ensureRoles(PLATFORM, Map.of("project.editor", Set.of("project_can_view", "project_can_delete"))));
        assertEquals(Set.of("project_can_delete", "project_can_view"),
                     await(relationshipService.read(PLATFORM, "role:project.editor")).stream().map(RelationshipTuple::relation).collect(Collectors.toSet()));
        await(relationshipService.ensureRoles(PLATFORM, model.roles()));

        await(relationshipService.ensure(PLATFORM, List.of(new RelationshipTuple("application:crm-roles", "application", "project:billing-roles"))));
        String bindingId = await(relationshipService.bind(PLATFORM, "project.editor", "user:sally-roles", "project:billing-roles"));
        assertTrue(await(relationshipService.holds(PLATFORM, new RelationshipTuple("role_binding:" + bindingId, "role_binding", "project:billing-roles"))));
        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-roles", "project_can_edit", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-roles", "project_can_delete", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
        // a binding of the organization admin on the organization reaches the project inside it
        await(relationshipService.ensure(PLATFORM, List.of(new RelationshipTuple("organization:acme-roles", "organization", "application:crm-roles"))));
        await(relationshipService.bind(PLATFORM, AuthzUtil.ORGANIZATION_ADMIN_ROLE, "user:marcus-roles", "organization:acme-roles"));
        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:marcus-roles", "project_can_delete", "project:billing-roles"), Consistency.HIGHER_CONSISTENCY)));
    }

    @Test
    void ensureAndRemoveAreIdempotent() throws Exception {
        await(storeService.ensureModel(PLATFORM, generator.platformModel(List.of(projectService()))));
        RelationshipTuple contained = new RelationshipTuple("application:crm-ensure", "application", "project:billing-ensure");
        RelationshipTuple member = new RelationshipTuple("user:sally-ensure", "member", "organization:acme-ensure");

        await(relationshipService.ensure(PLATFORM, List.of(contained, member)));
        await(relationshipService.ensure(PLATFORM, List.of(contained, member)));
        assertTrue(await(relationshipService.holds(PLATFORM, contained)));
        assertTrue(await(relationshipService.holds(PLATFORM, member)));
        await(relationshipService.remove(PLATFORM, List.of(contained)));
        await(relationshipService.remove(PLATFORM, List.of(contained, member)));
        assertFalse(await(relationshipService.holds(PLATFORM, contained)));
        assertFalse(await(relationshipService.holds(PLATFORM, member)));
    }

    @Test
    void writesBeyondOneRequestAreBatched() throws Exception {
        String modelId = await(storeService.ensureModel(PLATFORM, generator.platformModel(List.of(projectService()))));
        List<RelationshipTuple> members = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            members.add(new RelationshipTuple("user:member" + i, "member", "group:everyone-batch"));
        }

        await(relationshipService.write(PLATFORM, members, List.of()));

        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:member249", "member", "group:everyone-batch"), Consistency.HIGHER_CONSISTENCY)));
        await(relationshipService.write(PLATFORM, List.of(), members));
        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:member249", "member", "group:everyone-batch"), Consistency.HIGHER_CONSISTENCY)));
    }

    @Test
    void platformModelIdIsTheVersionTheStoreRuns() throws Exception {
        String modelId = await(storeService.ensureModel(PLATFORM, generator.platformModel(List.of(projectService()))));

        assertEquals(modelId, await(storeService.modelId(PLATFORM)));
    }

    @Test
    void unbindLeavesNothingOfABindingAndReadsByUserFindItsHolders() throws Exception {
        AuthzModel model = generator.platformModel(List.of(projectService()));
        String modelId = await(storeService.ensureModel(PLATFORM, model));
        await(relationshipService.ensureRoles(PLATFORM, model.roles()));
        await(relationshipService.ensure(PLATFORM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:billing-unbind"))));
        String bindingId = await(relationshipService.bind(PLATFORM, "project.editor", "user:sally-unbind", "project:billing-unbind"));
        String binding = "role_binding:" + bindingId;
        assertTrue(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.readByUser(PLATFORM, "role:project.editor", "role_binding")).stream()
                        .anyMatch(tuple -> tuple.object().equals(binding)));

        await(relationshipService.unbind(PLATFORM, bindingId, "project:billing-unbind"));

        assertFalse(await(relationshipService.check(PLATFORM, modelId, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.read(PLATFORM, binding)).isEmpty());
        assertFalse(await(relationshipService.holds(PLATFORM, new RelationshipTuple(binding, "role_binding", "project:billing-unbind"))));
        // a binding already gone leaves nothing to remove
        await(relationshipService.unbind(PLATFORM, bindingId, "project:billing-unbind"));
        await(relationshipService.remove(PLATFORM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:billing-unbind"))));
    }

    @Test
    void theStoreResolvedOnOneContextKeepsLaterCallersOnTheirOwn() throws Exception {
        // the store is resolved once and kept, so every caller after the first composes on a kept result
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(fga, properties);
        await(onContext(vertx.getOrCreateContext(), () -> node.modelId(PLATFORM)));
        Context later = vertx.getOrCreateContext();

        boolean stayed = await(onContext(later, () -> node.modelId(PLATFORM).map(id -> Vertx.currentContext() == later)));
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
                       function("findById", "project", "project", "can_view"),
                       function("save", "project", "project", "can_edit"),
                       function("deleteById", "project", "project", "can_delete"));
    }

    private static ServiceDefinition vmNodeService() {
        return service("VmNodeService", "vm_node", "platform",
                       function("register", "platform", "vm_node", "can_register_node"));
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
                                          .setObjectId("{id}")
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
