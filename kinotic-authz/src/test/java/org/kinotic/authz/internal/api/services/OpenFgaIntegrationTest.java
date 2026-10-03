package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.OpenFgaApi;
import io.vertx.core.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.internal.config.KinoticAuthzConfig;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the store and relationship services against a real OpenFGA: the platform store resolved by name,
 * a generated model written once and reused while it is unchanged, relationships written in batches, and
 * checks answering from them. OpenFGA runs from the image the dev stack declares when Docker is available,
 * else at the URL in the {@code OPENFGA_URL} environment variable, and the test is skipped when there is
 * neither.
 */
class OpenFgaIntegrationTest {

    private static GenericContainer<?> openfga;
    private static OpenFgaApi api;
    private static KinoticAuthzProperties properties;
    private static DefaultAuthzStoreService storeService;
    private static DefaultRelationshipService relationshipService;

    @BeforeAll
    static void setup() throws Exception {
        String apiUrl = System.getenv("OPENFGA_URL");
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
        properties = new KinoticAuthzProperties();
        properties.getAuthz().setApiUrl(apiUrl);
        api = new KinoticAuthzConfig().openFgaApi(properties);
        storeService = new DefaultAuthzStoreService(api, properties);
        relationshipService = new DefaultRelationshipService(api);
    }

    @AfterAll
    static void tearDown() {
        if (openfga != null) {
            openfga.stop();
        }
    }

    @Test
    void platformStoreIsFoundByNameAfterItsFirstCreation() throws Exception {
        String storeId = await(storeService.platformStoreId());

        assertEquals(storeId, await(storeService.platformStoreId()));
        // a second node resolves the same store rather than creating another of the name
        assertEquals(storeId, await(new DefaultAuthzStoreService(api, properties).platformStoreId()));
    }

    @Test
    void modelIsWrittenOnceAndReusedWhileUnchanged() throws Exception {
        String storeId = await(storeService.createStore("model-test"));
        AuthzModel model = new DefaultAuthzModelGenerator().platformModel(List.of(projectService()));

        String modelId = await(storeService.ensureModel(storeId, model));

        assertEquals(modelId, await(storeService.ensureModel(storeId, model)));
        AuthzModel grown = new DefaultAuthzModelGenerator().platformModel(List.of(projectService(), vmNodeService()));
        assertNotEquals(modelId, await(storeService.ensureModel(storeId, grown)));
    }

    @Test
    void checksAnswerFromWrittenRelationships() throws Exception {
        String storeId = await(storeService.createStore("check-test"));
        String modelId = await(storeService.ensureModel(storeId, new DefaultAuthzModelGenerator().platformModel(List.of(projectService()))));
        await(relationshipService.write(storeId, List.of(
                new RelationshipTuple("user:sally", "member", "organization:acme"),
                new RelationshipTuple("organization:acme", "organization", "application:crm"),
                new RelationshipTuple("application:crm", "application", "project:billing"),
                new RelationshipTuple("application:crm", "application", "project:shipping"),
                new RelationshipTuple("user:*", "project_can_edit", "role:project_editor"),
                new RelationshipTuple("role:project_editor", "role", "role_binding:sally_edits_billing"),
                new RelationshipTuple("user:sally", "member", "role_binding:sally_edits_billing"),
                new RelationshipTuple("role_binding:sally_edits_billing", "role_binding", "project:billing")), List.of()));

        assertTrue(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:sally", "project_can_edit", "project:billing"))));
        // editing implies viewing
        assertTrue(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:sally", "project_can_view", "project:billing"))));
        assertFalse(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:sally", "project_can_delete", "project:billing"))));
        assertFalse(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:sally", "project_can_view", "project:shipping"))));
        assertEquals(List.of("project:billing"),
                     await(relationshipService.listObjects(storeId, modelId, "user:sally", "project_can_view", "project")));

        await(relationshipService.write(storeId, List.of(), List.of(
                new RelationshipTuple("user:sally", "member", "role_binding:sally_edits_billing"))));
        assertFalse(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:sally", "project_can_edit", "project:billing"))));
    }

    @Test
    void writesBeyondOneRequestAreBatched() throws Exception {
        String storeId = await(storeService.createStore("batch-test"));
        String modelId = await(storeService.ensureModel(storeId, new DefaultAuthzModelGenerator().platformModel(List.of(projectService()))));
        List<RelationshipTuple> members = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            members.add(new RelationshipTuple("user:member" + i, "member", "group:everyone"));
        }

        await(relationshipService.write(storeId, members, List.of()));

        assertTrue(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:member249", "member", "group:everyone"))));
        await(relationshipService.write(storeId, List.of(), members));
        assertFalse(await(relationshipService.check(storeId, modelId, new RelationshipTuple("user:member249", "member", "group:everyone"))));
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
