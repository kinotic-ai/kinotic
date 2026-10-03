package org.kinotic.core.internal.api.directory;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the checks {@link AuthzDecorators} derives from a service's shape, and the ones it takes from
 * {@link AuthzCheck}, resolve to the templates the gateway evaluates.
 */
public class AuthzDecoratorsTest {

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Project implements Identifiable<String> {
        private String id;
        private String applicationId;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Registration implements Identifiable<String> {
        private String id;
    }

    @AuthzResource(value = "project", parent = "application")
    public interface ProjectService {
        CompletableFuture<Project> findById(String id);

        CompletableFuture<Page<Project>> findAllForApplication(String applicationId, Pageable pageable);

        CompletableFuture<Project> save(Project value);

        CompletableFuture<Project> createProjectIfNotExist(Project project);

        @AuthzCheck(permission = "can_edit")
        CompletableFuture<Void> retryRepoInitialization(String projectId);

        @AuthzCheck(permission = "can_deploy", implies = "can_view")
        CompletableFuture<Void> deploy(Participant participant, String projectId);

        CompletableFuture<Long> count();
    }

    public static class DefaultProjectService implements ProjectService {
        @Override
        public CompletableFuture<Project> findById(String id) {
            return null;
        }

        @Override
        public CompletableFuture<Page<Project>> findAllForApplication(String applicationId, Pageable pageable) {
            return null;
        }

        @Override
        public CompletableFuture<Project> save(Project value) {
            return null;
        }

        @Override
        public CompletableFuture<Project> createProjectIfNotExist(Project project) {
            return null;
        }

        @Override
        public CompletableFuture<Void> retryRepoInitialization(String projectId) {
            return null;
        }

        @Override
        public CompletableFuture<Void> deploy(Participant participant, String projectId) {
            return null;
        }

        @Override
        public CompletableFuture<Long> count() {
            return null;
        }
    }

    @AuthzResource("entity")
    public interface EntityService {
        @AuthzCheck(resource = "{entityDefinitionId}", objectId = "{id}")
        CompletableFuture<Object> findById(String entityDefinitionId, String id);
    }

    public static class DefaultEntityService implements EntityService {
        @Override
        public CompletableFuture<Object> findById(String entityDefinitionId, String id) {
            return null;
        }
    }

    @AuthzResource(value = "vm_node", parent = "platform")
    public interface VmNodeService {
        @AuthzCheck(resource = "platform", permission = "can_register_node")
        CompletableFuture<Registration> register(Registration registration);

        @AuthzCheck(permission = "can_heartbeat", objectId = "{registration.id}")
        CompletableFuture<Void> heartbeat(Registration registration);

        CompletableFuture<Void> deleteByVmNodeId(String vmNodeId);
    }

    public static class DefaultVmNodeService implements VmNodeService {
        @Override
        public CompletableFuture<Registration> register(Registration registration) {
            return null;
        }

        @Override
        public CompletableFuture<Void> heartbeat(Registration registration) {
            return null;
        }

        @Override
        public CompletableFuture<Void> deleteByVmNodeId(String vmNodeId) {
            return null;
        }
    }

    @AuthzResource("widget")
    public interface UnderivableService {
        CompletableFuture<Void> frobnicate(String id);
    }

    public static class DefaultUnderivableService implements UnderivableService {
        @Override
        public CompletableFuture<Void> frobnicate(String id) {
            return null;
        }
    }

    @AuthzResource("widget")
    public interface MisreferencingService {
        @AuthzCheck(permission = "can_view", objectId = "{missing}")
        CompletableFuture<Void> find(String id);
    }

    public static class DefaultMisreferencingService implements MisreferencingService {
        @Override
        public CompletableFuture<Void> find(String id) {
            return null;
        }
    }

    public interface PlainService {
        CompletableFuture<Void> findById(String id);
    }

    public static class DefaultPlainService implements PlainService {
        @Override
        public CompletableFuture<Void> findById(String id) {
            return null;
        }
    }

    @Test
    public void resourceDecoratorCarriesTypeAndParent() {
        AuthzResourceC3Decorator resource = AuthzDecorators.resourceOf(ProjectService.class);

        assertEquals("project", resource.getResourceType());
        assertEquals("application", resource.getParent());
        assertNull(AuthzDecorators.resourceOf(EntityService.class).getParent());
        assertNull(AuthzDecorators.resourceOf(PlainService.class));
    }

    @Test
    public void viewVerbsCheckTheNamedResource() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator findById = checks.get("findById");
        assertEquals("project", findById.getResource());
        assertEquals("{id}", findById.getObjectId());
        assertEquals("project", findById.getPermissionResource());
        assertEquals("can_view", findById.getPermission());
        assertEquals(Map.of("id", 0), findById.getParameterBodyIndexes());
    }

    @Test
    public void listingWithinTheParentChecksTheParent() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator findAll = checks.get("findAllForApplication");
        assertEquals("application", findAll.getResource());
        assertEquals("{applicationId}", findAll.getObjectId());
        assertEquals("project", findAll.getPermissionResource());
        assertEquals("can_view", findAll.getPermission());
        assertEquals(Map.of("applicationId", 0), findAll.getParameterBodyIndexes());
    }

    @Test
    public void saveChecksTheIdentifiableArgument() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator save = checks.get("save");
        assertEquals("project", save.getResource());
        assertEquals("{value.id}", save.getObjectId());
        assertEquals("can_edit", save.getPermission());
        assertEquals(Map.of("value", 0), save.getParameterBodyIndexes());
    }

    @Test
    public void createChecksTheParentNamedByTheArgument() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator create = checks.get("createProjectIfNotExist");
        assertEquals("application", create.getResource());
        assertEquals("{project.applicationId}", create.getObjectId());
        assertEquals("project", create.getPermissionResource());
        assertEquals("can_edit", create.getPermission());
    }

    @Test
    public void declaredPermissionKeepsTheDerivedObject() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator retry = checks.get("retryRepoInitialization");
        assertEquals("project", retry.getResource());
        assertEquals("{projectId}", retry.getObjectId());
        assertEquals("can_edit", retry.getPermission());
        assertTrue(retry.getImplies().isEmpty());
    }

    @Test
    public void participantParametersHaveNoBodyIndex() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator deploy = checks.get("deploy");
        assertEquals("{projectId}", deploy.getObjectId());
        assertEquals("can_deploy", deploy.getPermission());
        assertEquals(List.of("can_view"), deploy.getImplies());
        assertEquals(Map.of("projectId", 0), deploy.getParameterBodyIndexes());
    }

    @Test
    public void aFunctionNamingNothingChecksTheCallersScope() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(ProjectService.class, DefaultProjectService.class);

        AuthzCheckC3Decorator count = checks.get("count");
        assertEquals("application", count.getResource());
        assertEquals("{@applicationId}", count.getObjectId());
        assertEquals("project", count.getPermissionResource());
        assertTrue(count.getParameterBodyIndexes().isEmpty());
    }

    @Test
    public void templatedResourceReferencesBothParameters() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(EntityService.class, DefaultEntityService.class);

        AuthzCheckC3Decorator findById = checks.get("findById");
        assertEquals("{entityDefinitionId}", findById.getResource());
        assertEquals("{id}", findById.getObjectId());
        assertEquals("{entityDefinitionId}", findById.getPermissionResource());
        assertEquals("can_view", findById.getPermission());
        assertEquals(Map.of("entityDefinitionId", 0, "id", 1), findById.getParameterBodyIndexes());
    }

    @Test
    public void aCheckOnThePlatformUsesItsFixedId() {
        Map<String, AuthzCheckC3Decorator> checks = AuthzDecorators.checksOf(VmNodeService.class, DefaultVmNodeService.class);

        AuthzCheckC3Decorator register = checks.get("register");
        assertEquals("platform", register.getResource());
        assertEquals("kinotic", register.getObjectId());
        assertEquals("vm_node", register.getPermissionResource());
        assertEquals("can_register_node", register.getPermission());
        assertTrue(register.getParameterBodyIndexes().isEmpty());

        AuthzCheckC3Decorator heartbeat = checks.get("heartbeat");
        assertEquals("vm_node", heartbeat.getResource());
        assertEquals("{registration.id}", heartbeat.getObjectId());
        assertEquals(Map.of("registration", 0), heartbeat.getParameterBodyIndexes());

        AuthzCheckC3Decorator delete = checks.get("deleteByVmNodeId");
        assertEquals("{vmNodeId}", delete.getObjectId());
        assertEquals("can_delete", delete.getPermission());
    }

    @Test
    public void aFunctionDerivingNoPermissionFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> AuthzDecorators.checksOf(UnderivableService.class, DefaultUnderivableService.class));

        assertTrue(e.getMessage().contains("frobnicate"));
    }

    @Test
    public void aTemplateNamingAnUnknownParameterFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> AuthzDecorators.checksOf(MisreferencingService.class, DefaultMisreferencingService.class));

        assertTrue(e.getMessage().contains("missing"));
    }

    @Test
    public void aServiceWithoutAResourceHasNoChecks() {
        assertTrue(AuthzDecorators.checksOf(PlainService.class, DefaultPlainService.class).isEmpty());
    }

    @Test
    public void applyAttachesTheDecoratorsToTheDefinition() {
        ServiceDefinition definition = new ServiceDefinition()
                .setNamespace("org.kinotic.test")
                .setName("ProjectService")
                .addFunction(new FunctionDefinition().setName("findById"))
                .addFunction(new FunctionDefinition().setName("count"));

        AuthzDecorators.apply(ProjectService.class, DefaultProjectService.class, definition);

        assertEquals("project", definition.findDecorator(AuthzResourceC3Decorator.class).getResourceType());
        for (FunctionDefinition function : definition.getFunctions()) {
            assertNotNull(function.findDecorator(AuthzCheckC3Decorator.class), function.getName());
        }
    }

}
