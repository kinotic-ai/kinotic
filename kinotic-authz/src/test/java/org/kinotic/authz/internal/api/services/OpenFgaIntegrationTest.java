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
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
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
import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Exercises the store and relationship services against a real OpenFGA: the system store, created ahead of
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
        createSystemStoreAheadOfTheServers();
    }

    /**
     * The role terraform or the dev stack plays before a server starts: the one store of the system's name.
     */
    private static void createSystemStoreAheadOfTheServers() throws Exception {
        KinoticAuthzProperties properties = new KinoticAuthzProperties();
        properties.getAuthz().setApiUrl(apiUrl);
        OpenFgaService fga = new OpenFgaService(properties);
        boolean exists = await(fga.listStores(100, null, DefaultAuthzStoreService.SYSTEM_STORE_NAME))
                .getStores().stream()
                .anyMatch(store -> DefaultAuthzStoreService.SYSTEM_STORE_NAME.equals(store.getName()));
        if (!exists) {
            await(fga.createStore(new CreateStoreRequest().name(DefaultAuthzStoreService.SYSTEM_STORE_NAME)));
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
        assertEquals(modelId, await(new DefaultAuthzStoreService(fga, properties, vertx).ensureModel(SYSTEM, model)));
        String systemStoreId = await(fga.listStores(100, null, DefaultAuthzStoreService.SYSTEM_STORE_NAME))
                .getStores().getFirst().getId();
        assertEquals(modelId, await(fga.readAuthorizationModels(systemStoreId, 1, null))
                .getAuthorizationModels().getFirst().getId());
    }

    @Test
    void anUnreachableEngineFailsTheCallAndTheNodeKeepsRunning() {
        KinoticAuthzProperties unreachable = new KinoticAuthzProperties();
        unreachable.getAuthz().setApiUrl("http://127.0.0.1:1");
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(new OpenFgaService(unreachable), unreachable, vertx);
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
        assertEquals(storeId, await(new DefaultAuthzStoreService(fga, properties, vertx).ensureStore(store)));
        String modelId = await(storeService.ensureModel(store, model));
        assertEquals(modelId, await(storeService.modelId(store)));
        await(relationshipService.ensure(store, List.of(new RelationshipTuple("user:bob", "end_user", "application:" + store))));
        assertTrue(await(relationshipService.holds(store, new RelationshipTuple("user:bob", "end_user", "application:" + store))));

        String name = await(fga.getStore(storeId)).getName();
        // a duplicate a concurrent creator left under the name goes with the store
        await(fga.createStore(new CreateStoreRequest().name(name)));

        await(storeService.deleteStore(store));

        assertThrows(ExecutionException.class, () -> await(storeService.modelId(store)));
        assertTrue(await(fga.listStores(10, null, name)).getStores().isEmpty());
        // a store already gone leaves nothing to delete
        await(storeService.deleteStore(store));
    }

    @Test
    void aModelIsWrittenOnlyOnceTheRolesItGrantsThroughAreInStep() throws Exception {
        String store = "roles-first-" + System.nanoTime();
        AuthzModel kernel = generator.applicationModel(List.of(entitiesRepository()), List.of());
        AuthzModel grown = generator.applicationModel(List.of(entitiesRepository()), List.of(reportService()));
        String storeId = await(storeService.ensureStore(store));
        await(relationshipService.ensureModelWithRoles(store, kernel));

        String grownModelId = await(relationshipService.ensureModelWithRoles(store, grown));

        // the version before the grown one carries the report's role relations, and no type grants through them
        List<AuthorizationModel> versions = await(fga.readAuthorizationModels(storeId, 10, null)).getAuthorizationModels();
        assertEquals(grownModelId, versions.getFirst().getId());
        AuthorizationModel bridge = versions.get(1);
        assertTrue(relationsOf(bridge, "tenant_role").contains("report_can_read"));
        assertFalse(relationsOf(bridge, "tenant").contains("report_can_read"));
        assertTrue(relationsOf(bridge, "tenant").contains("entity_definition_can_read"));
        assertTrue(await(relationshipService.holds(store, new RelationshipTuple("user:*", "report_can_read", AuthzUtil.roleObject("tenant.admin")))));
        // a model in step writes nothing more
        assertEquals(grownModelId, await(relationshipService.ensureModelWithRoles(store, grown)));
        assertEquals(versions.size(), await(fga.readAuthorizationModels(storeId, 10, null)).getAuthorizationModels().size());
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
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties, vertx);
        DefaultRelationshipService relationshipsElsewhere = new DefaultRelationshipService(fga, elsewhere);
        String storeId = await(storeService.ensureStore(store));
        String modelId = await(storeService.ensureModel(store, model));
        await(relationshipService.ensure(store, List.of(bob)));
        // another node resolved the store and read its model before the application was deleted
        assertEquals(storeId, await(elsewhere.ensureStore(store)));
        assertEquals(modelId, await(elsewhere.modelId(store)));
        await(storeService.deleteStore(store));

        String recreated = await(storeService.ensureStore(store));
        AuthzModel grown = generator.applicationModel(List.of(), List.of(reportService()));
        String grownModelId = await(storeService.ensureModel(store, grown));
        await(relationshipService.ensureRoles(store, grown.roles()));
        await(relationshipService.bind(store, "report.viewer", "user:bob", "tenant:t1"));

        assertNotEquals(storeId, recreated);
        // the engine keeps answering for the deleted store, so the other node answers from it until the retention
        // has passed and it confirms the id it kept, finds the store gone and resolves the name again
        long deadline = System.currentTimeMillis() + DefaultAuthzStoreService.RETENTION_MILLIS + 15_000;
        while (await(relationshipsElsewhere.holds(store, bob)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertFalse(await(relationshipsElsewhere.holds(store, bob)));
        assertEquals(grownModelId, await(elsewhere.modelId(store)));
        assertTrue(await(relationshipsElsewhere.check(store, new RelationshipTuple("user:bob", "report_can_read", "tenant:t1"),
                                                      Consistency.HIGHER_CONSISTENCY)));
        await(storeService.deleteStore(store));
    }

    @Test
    void aCheckOnARelationTheKeptVersionLacksIsAnsweredByTheNewest() throws Exception {
        String store = "grown-" + System.nanoTime();
        DefaultAuthzStoreService elsewhere = new DefaultAuthzStoreService(fga, properties, vertx);
        DefaultRelationshipService relationshipsElsewhere = new DefaultRelationshipService(fga, elsewhere);
        await(storeService.ensureStore(store));
        String modelId = await(storeService.ensureModel(store, generator.applicationModel(List.of(), List.of())));
        // another node read the store's version before the model grew
        assertEquals(modelId, await(elsewhere.modelId(store)));

        AuthzModel grown = generator.applicationModel(List.of(), List.of(reportService()));
        await(storeService.ensureModel(store, grown));
        await(relationshipService.ensureRoles(store, grown.roles()));
        await(relationshipService.bind(store, "report.viewer", "user:bob", "tenant:t1"));

        assertEquals(modelId, await(elsewhere.modelId(store)));
        assertTrue(await(relationshipsElsewhere.check(store, new RelationshipTuple("user:bob", "report_can_read", "tenant:t1"),
                                                      Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of("tenant:t1"), await(relationshipsElsewhere.listObjects(store, "user:bob", "report_can_read", "tenant",
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
        await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));
        // the engine's ListObjects stops at 1000 objects by default
        List<RelationshipTuple> written = new ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            written.add(new RelationshipTuple("application:crm-lists", "application", "project:p" + i + "-lists"));
        }
        await(relationshipService.ensure(SYSTEM, written));
        String bindingId = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-lists", "application:crm-lists"));

        assertEquals(1100, await(relationshipService.listObjects(SYSTEM, "user:sally-lists", "project_can_read", "project",
                                                                 Consistency.HIGHER_CONSISTENCY)).size());
        await(relationshipService.revoke(SYSTEM, bindingId, "application:crm-lists"));
        await(relationshipService.remove(SYSTEM, written));
    }

    @Test
    void checksAnswerFromWrittenRelationships() throws Exception {
        await(storeService.ensureModel(SYSTEM, generator.systemModel(List.of(projectService()))));
        List<RelationshipTuple> written = List.of(
                new RelationshipTuple("user:sally-checks", "member", "organization:acme-checks"),
                new RelationshipTuple("organization:acme-checks", "organization", "application:crm-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:billing-checks"),
                new RelationshipTuple("application:crm-checks", "application", "project:shipping-checks"),
                new RelationshipTuple("user:*", "project_can_edit", "project_role:project.editor_checks"),
                new RelationshipTuple("project_role:project.editor_checks", "role", "project_role_binding:sally_edits_billing_checks"),
                new RelationshipTuple("user:sally-checks", "member", "project_role_binding:sally_edits_billing_checks"),
                new RelationshipTuple("project_role_binding:sally_edits_billing_checks", "role_binding", "project:billing-checks"));
        await(relationshipService.ensure(SYSTEM, written));

        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // editing implies reading
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_read", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_delete", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_read", "project:shipping-checks"), Consistency.HIGHER_CONSISTENCY)));
        assertEquals(List.of("project:billing-checks"),
                     await(relationshipService.listObjects(SYSTEM, "user:sally-checks", "project_can_read", "project", Consistency.HIGHER_CONSISTENCY)));
        // the grant of a role bundling only editing explains reading, which editing implies
        Grant editing = new Grant("sally_edits_billing_checks", "project.editor_checks", new Subject(SubjectType.USER, "sally-checks"),
                                  new Resource("project", "billing-checks"));
        assertEquals(List.of(editing), await(relationshipService.explain(SYSTEM, editing.subject(),
                                                                       new RelationshipTuple("user:sally-checks", "project_can_read", "project:billing-checks"),
                                                                       List.of(), List.of(editing))).through());
        assertEquals(List.of(), await(relationshipService.explain(SYSTEM, editing.subject(),
                                                                  new RelationshipTuple("user:sally-checks", "project_can_delete", "project:billing-checks"),
                                                                  List.of(), List.of(editing))).through());

        await(relationshipService.remove(SYSTEM, List.of(
                new RelationshipTuple("user:sally-checks", "member", "project_role_binding:sally_edits_billing_checks"))));
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-checks", "project_can_edit", "project:billing-checks"), Consistency.HIGHER_CONSISTENCY)));
        // the store is shared with the other tests and with a run against a persistent engine, so nothing written here stays
        await(relationshipService.remove(SYSTEM, written));
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
        String bindingId = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-roles", "project:billing-roles"));
        assertTrue(await(relationshipService.holds(SYSTEM, new RelationshipTuple(AuthzUtil.bindingObject("project:billing-roles", bindingId), "role_binding", "project:billing-roles"))));
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
    void revokeLeavesNothingOfABindingAndReadsByUserFindItsHolders() throws Exception {
        AuthzModel model = generator.systemModel(List.of(projectService()));
        await(storeService.ensureModel(SYSTEM, model));
        await(relationshipService.ensureRoles(SYSTEM, model.roles()));
        await(relationshipService.ensure(SYSTEM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:billing-unbind"))));
        String bindingId = await(relationshipService.bind(SYSTEM, "project.editor", "user:sally-unbind", "project:billing-unbind"));
        String binding = AuthzUtil.bindingObject("project:billing-unbind", bindingId);
        assertTrue(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.readByUser(SYSTEM, AuthzUtil.roleObject("project.editor"), "project_role_binding")).stream()
                        .anyMatch(tuple -> tuple.object().equals(binding)));

        await(relationshipService.revoke(SYSTEM, bindingId, "project:billing-unbind"));

        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:sally-unbind", "project_can_edit", "project:billing-unbind"), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationshipService.read(SYSTEM, binding)).isEmpty());
        assertFalse(await(relationshipService.holds(SYSTEM, new RelationshipTuple(binding, "role_binding", "project:billing-unbind"))));
        // a grant already revoked is no grant made on the object
        assertThrows(ExecutionException.class, () -> await(relationshipService.revoke(SYSTEM, bindingId, "project:billing-unbind")));
        await(relationshipService.remove(SYSTEM, List.of(new RelationshipTuple("application:crm-unbind", "application", "project:billing-unbind"))));
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
        assertTrue(await(relationshipService.read(SYSTEM, AuthzUtil.bindingObject("project:billing-gone", kept))).isEmpty());
        assertFalse(await(relationshipService.check(SYSTEM, new RelationshipTuple("user:marcus-gone", "project_can_edit", "project:billing-gone"), Consistency.HIGHER_CONSISTENCY)));

        // a store that does not exist holds nothing of anyone
        await(relationshipService.removeSubject("application:acme.no-such-application-gone", "user:sally-gone"));
        await(relationshipService.removeObject(SYSTEM, "project:payroll-gone"));
    }

    @Test
    void theStoreResolvedOnOneContextKeepsLaterCallersOnTheirOwn() throws Exception {
        // the store is resolved once and kept, so every caller after the first composes on a kept result
        DefaultAuthzStoreService node = new DefaultAuthzStoreService(fga, properties, vertx);
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

    // an application's own service, declared on the tenant, so the tenant carries its permission
    private static ServiceDefinition reportService() {
        return service("ReportService", "report", "tenant",
                       function("findById", "report", "report", "can_read"));
    }

    // the system's entities repository, whose declaration gives a definition's rows their permissions
    private static ServiceDefinition entitiesRepository() {
        return service("JsonEntitiesRepository", "entity_definition", "application",
                       function("findById", "entity_definition", "entity_definition", "can_read"));
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
