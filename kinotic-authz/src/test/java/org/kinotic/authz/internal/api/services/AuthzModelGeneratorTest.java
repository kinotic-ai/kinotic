package org.kinotic.authz.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link DefaultAuthzModelGenerator} turns the directory's decorators into the authorization model:
 * the kernel types, one resource type per service and entity, typed permissions carried by every ancestor,
 * and the implication lattice.
 */
public class AuthzModelGeneratorTest {

    private final DefaultAuthzModelGenerator generator = new DefaultAuthzModelGenerator();

    @Test
    public void aFunctionMarkedUncheckedAddsNoPermission() {
        FunctionDefinition ping = new FunctionDefinition().setName("ping");
        ping.setDecorators(List.of(new AuthzCheckC3Decorator().setUnchecked(true)));
        AuthzModel model = generator.systemModel(List.of(service("ReportService", "report", "organization",
                                                                   function("findReports", "report", "report", "can_read"), ping)));

        assertEquals(Set.of("can_read"), model.permissions().get("report"));
    }

    private static ServiceDefinition service(String name, String type, String parent, FunctionDefinition... functions) {
        return service(name, type, parent, List.of(), functions);
    }

    // the system's entities repository as the directory holds it: the rows' permissions, a ladder
    private static ServiceDefinition entitiesRepository() {
        return service("JsonEntitiesRepository", "entity_definition", "application",
                       function("count", "entity_definition", "entity_definition", "can_read"),
                       function("findById", "entity_definition", "entity_definition", "can_read"),
                       function("save", "entity_definition", "entity_definition", "can_edit"),
                       function("update", "entity_definition", "entity_definition", "can_edit"),
                       function("deleteById", "entity_definition", "entity_definition", "can_delete"));
    }

    private static ServiceDefinition service(String name,
                                             String type,
                                             String parent,
                                             List<AuthzRoleDeclaration> roles,
                                             FunctionDefinition... functions) {
        ServiceDefinition ret = new ServiceDefinition()
                .setNamespace("org.kinotic.test")
                .setName(name);
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(type).setParent(parent).setRoles(roles)));
        for (FunctionDefinition function : functions) {
            ret.addFunction(function);
        }
        return ret;
    }

    private static AuthzRoleDeclaration role(String id, String... permissions) {
        return new AuthzRoleDeclaration().setId(id).setPermissions(List.of(permissions));
    }

    private static FunctionDefinition function(String name,
                                               String resource,
                                               String permissionResource,
                                               String permission,
                                               String... implies) {
        FunctionDefinition ret = new FunctionDefinition().setName(name);
        ret.setDecorators(List.of(new AuthzCheckC3Decorator()
                                          .setResource(resource)
                                          .setResourceId("{id}")
                                          .setPermissionResource(permissionResource)
                                          .setPermission(permission)
                                          .setImplies(List.of(implies))));
        return ret;
    }

    private static List<ServiceDefinition> systemServices() {
        return List.of(service("ProjectService", "project", "application",
                               function("findById", "project", "project", "can_read"),
                               function("save", "project", "project", "can_edit"),
                               function("deleteById", "project", "project", "can_delete"),
                               function("deploy", "project", "project", "can_deploy", "can_read"),
                               function("count", "application", "project", "can_read")),
                       service("VmNodeService", "vm_node", "system",
                               List.of(role("vm_node.registrar", "can_register_node"), role("vm_node.agent", "can_report", "can_read")),
                               function("register", "system", "vm_node", "can_register_node"),
                               function("heartbeat", "vm_node", "vm_node", "can_report"),
                               function("findById", "vm_node", "vm_node", "can_read")),
                       service("MemberService", "organization", null,
                               function("findMembers", "organization", "organization", "can_read_members"),
                               function("removeMember", "organization", "organization", "can_manage_members")),
                       service("ClusterService", "system", null,
                               function("getClusterInfo", "system", "system", "can_read_cluster"),
                               function("createMachine", "system", "system", "can_manage_machines")));
    }

    private static JsonNode type(AuthzModel model, String name) {
        JsonNode ret = null;
        for (JsonNode typeDefinition : model.definition().get("type_definitions")) {
            if (name.equals(typeDefinition.get("type").asString())) {
                ret = typeDefinition;
            }
        }
        assertTrue(ret != null, "type " + name + " is not in the model");
        return ret;
    }

    private static Set<String> typeNames(AuthzModel model) {
        Set<String> ret = new LinkedHashSet<>();
        for (JsonNode typeDefinition : model.definition().get("type_definitions")) {
            ret.add(typeDefinition.get("type").asString());
        }
        return ret;
    }

    private static List<String> directTypes(JsonNode type, String relation) {
        List<String> ret = new ArrayList<>();
        for (JsonNode reference : type.get("metadata").get("relations").get(relation).get("directly_related_user_types")) {
            String name = reference.get("type").asString();
            if (reference.has("relation")) {
                name += "#" + reference.get("relation").asString();
            } else if (reference.has("wildcard")) {
                name += ":*";
            }
            ret.add(name);
        }
        return ret;
    }

    /**
     * The sources of a set operator relation, in the form {@code ttu:<tupleset>-><relation>} or
     * {@code computed:<relation>}.
     */
    private static List<String> children(JsonNode type, String relation, String operator) {
        List<String> ret = new ArrayList<>();
        for (JsonNode child : type.get("relations").get(relation).get(operator).get("child")) {
            ret.add(source(child));
        }
        return ret;
    }

    // One source of a relation: a tuple-to-userset, a computed userset, or a nested set of them
    private static String source(JsonNode node) {
        String ret;
        if (node.has("tupleToUserset")) {
            JsonNode ttu = node.get("tupleToUserset");
            ret = "ttu:" + ttu.get("tupleset").get("relation").asString() + "->" + ttu.get("computedUserset").get("relation").asString();
        } else if (node.has("computedUserset")) {
            ret = "computed:" + node.get("computedUserset").get("relation").asString();
        } else {
            String operator = node.has("union") ? "union" : "intersection";
            List<String> nested = new ArrayList<>();
            for (JsonNode child : node.get(operator).get("child")) {
                nested.add(source(child));
            }
            ret = operator + ":" + nested;
        }
        return ret;
    }

    @Test
    public void systemModelHasTheKernelAndOneTypePerService() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals("1.1", model.definition().get("schema_version").asString());
        assertEquals(withRoleTypes("application", "organization", "system", "project", "tenant", "vm_node"), typeNames(model));
        assertEquals(List.of("application"), directTypes(type(model, "project"), "application"));
        assertEquals(List.of("user"), directTypes(type(model, "organization"), "member"));
        assertEquals(List.of("user"), directTypes(type(model, "application"), "end_user"));
        assertEquals(List.of("user", "group#member"), directTypes(type(model, "group"), "member"));
    }

    @Test
    public void everyAncestorCarriesADescendantsPermissions() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:application->project_can_edit"),
                     children(type(model, "project"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:organization->project_can_edit"),
                     children(type(model, "application"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:system->project_can_edit"),
                     children(type(model, "organization"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete"),
                     children(type(model, "system"), "project_can_edit", "union"));
        assertFalse(type(model, "tenant").get("relations").has("project_can_edit"));
    }

    @Test
    public void declaredAndDerivedImplicationsFormTheLattice() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(List.of("ttu:role_binding->project_can_read", "computed:project_can_deploy", "computed:project_can_edit",
                             "ttu:application->project_can_read"),
                     children(type(model, "project"), "project_can_read", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_delete", "ttu:application->project_can_delete"),
                     children(type(model, "project"), "project_can_delete", "union"));
        // a single source is the relation itself, as the engine refuses a union of one
        JsonNode registerNode = type(model, "system").get("relations").get("vm_node_can_register_node");
        assertFalse(registerNode.has("union"));
        assertEquals("role_binding", registerNode.get("tupleToUserset").get("tupleset").get("relation").asString());
        assertEquals("vm_node_can_register_node", registerNode.get("tupleToUserset").get("computedUserset").get("relation").asString());
    }

    @Test
    public void rolesBundleTheirHomesPermissionsAndBindingsNarrowThemToMembers() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(List.of("user:*"), directTypes(type(model, "project_role"), "grant"));
        assertEquals(List.of("user:*"), directTypes(type(model, "project_role"), "project_can_deploy"));
        // a role homed at a type bundles only the permissions of the type and of the types inside it
        assertFalse(type(model, "project_role").get("relations").has("organization_can_read_members"));
        assertFalse(type(model, "organization_role").get("relations").has("system_can_read_cluster"));
        assertEquals(List.of("user:*"), directTypes(type(model, "system_role"), "project_can_deploy"));
        assertEquals(List.of("user:*"), directTypes(type(model, "system_role"), "vm_node_can_register_node"));
        assertEquals(List.of("user", "group#member", "organization#member", "application#end_user", "tenant#member"),
                     directTypes(type(model, "project_role_binding"), "member"));
        assertEquals(List.of("computed:member", "ttu:role->project_can_deploy"),
                     children(type(model, "project_role_binding"), "project_can_deploy", "intersection"));
        // a resource accepts only the binding of its own type
        assertEquals(List.of("project_role_binding"), directTypes(type(model, "project"), "role_binding"));
        assertEquals(List.of("organization_role_binding"), directTypes(type(model, "organization"), "role_binding"));
    }

    @Test
    public void aBindingGrantsOnlyRolesHomedAtItsTypeOrInsideItWithinItsTrustBoundary() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(List.of("project_role"), directTypes(type(model, "project_role_binding"), "role"));
        assertEquals(List.of("application_role", "project_role"), directTypes(type(model, "application_role_binding"), "role"));
        // an organization grants none of a tenant's roles, and the system none of an organization's
        assertEquals(List.of("application_role", "organization_role", "project_role"),
                     directTypes(type(model, "organization_role_binding"), "role"));
        assertEquals(List.of("system_role", "vm_node_role"), directTypes(type(model, "system_role_binding"), "role"));
        assertEquals(List.of("tenant_role"), directTypes(type(model, "tenant_role_binding"), "role"));
        assertTrue(model.grantable("project.editor", "organization"));
        assertFalse(model.grantable(AuthzConstants.ORGANIZATION_ADMIN_ROLE, "project"));
        assertFalse(model.grantable("project.editor", "system"));
        assertFalse(model.grantable("tenant.admin", "organization"));
        assertEquals(Set.of("application", "organization", "project"), model.grantableOn("project", "organization"));
        assertEquals(Set.of(), model.grantableOn(AuthzConstants.SYSTEM_TYPE, "organization"));
        assertEquals(Set.of("organization", "project"), model.catalog("organization").keySet());
    }

    // The kernel types, and each resource type beside its role and binding types
    private static Set<String> withRoleTypes(String... resourceTypes) {
        Set<String> ret = new TreeSet<>(Set.of("user", "group"));
        for (String type : resourceTypes) {
            ret.addAll(Set.of(type, AuthzUtil.roleType(type), AuthzUtil.roleBindingType(type)));
        }
        return ret;
    }

    private static Set<String> applicationTypes() {
        Set<String> ret = withRoleTypes("application", "entity_definition", "tenant");
        ret.addAll(Set.of("tenant_definition", "tenant_definition_role_binding"));
        return ret;
    }

    @Test
    public void aResolvedCheckAddsEveryPermissionItsResolverMayRequire() {
        FunctionDefinition runQuery = new FunctionDefinition().setName("runQuery");
        runQuery.setDecorators(List.of(new AuthzCheckC3Decorator()
                                               .setResource("report")
                                               .setResourceId("{id}")
                                               .setPermissionResource("report")
                                               .setPermissionResolver("statement")
                                               .setPossiblePermissions(List.of(new AuthzPermissionChoice("can_read", "the query reads rows"),
                                                                               new AuthzPermissionChoice("can_delete", "the query is a DELETE")))));
        AuthzModel model = generator.systemModel(List.of(service("ReportService", "report", "organization", runQuery)));

        assertEquals(Set.of("can_read", "can_delete"), model.permissions().get("report"));
    }

    @Test
    public void catalogListsTheShortPermissionsOfEachType() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(Map.of("project", Set.of("can_delete", "can_deploy", "can_edit", "can_read"),
                            "vm_node", Set.of("can_register_node", "can_report", "can_read"),
                            "organization", Set.of("can_read_members", "can_manage_members"),
                            "system", Set.of("can_read_cluster", "can_manage_machines")),
                     model.permissions());
    }

    @Test
    public void builtInRolesFollowTheTypesAndTheTree() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(Set.of("project_can_read"), model.roles().get("project.viewer"));
        assertEquals(Set.of("project_can_read", "project_can_edit", "project_can_deploy"), model.roles().get("project.editor"));
        assertEquals(Set.of("project_can_read", "project_can_edit", "project_can_deploy", "project_can_delete"), model.roles().get("project.admin"));
        // the admin of a container holds everything inside it and its own, the developer everything but the container's own
        assertEquals(Set.of("project_can_read", "project_can_edit", "project_can_deploy", "project_can_delete",
                            "organization_can_read_members", "organization_can_manage_members"),
                     model.roles().get(AuthzConstants.ORGANIZATION_ADMIN_ROLE));
        assertEquals(model.roles().get("project.admin"), model.roles().get("application.admin"));
        assertEquals(model.roles().get("project.admin"), model.roles().get(AuthzConstants.APPLICATION_DEVELOPER_ROLE));
        // a permission named can_read_<something> only reads, so the viewer holds it
        assertEquals(Set.of("organization_can_read_members"), model.roles().get("organization.viewer"));
        // deciding who may act, as managing members or machines does, is an administrator's, never an editor's
        assertEquals(Set.of("organization_can_read_members"), model.roles().get("organization.editor"));
        assertEquals(Set.of("system_can_read_cluster"), model.roles().get("system.editor"));
        // a type with no reading permission has no viewer, and a role bundling nothing is not a role
        assertFalse(model.roles().containsKey("tenant.viewer"));
        assertFalse(model.roles().containsKey("tenant.admin"));
    }

    @Test
    public void declaredRolesBundleTheirTypesPermissions() {
        AuthzModel model = generator.systemModel(systemServices());

        assertEquals(Set.of("vm_node_can_register_node"), model.roles().get("vm_node.registrar"));
        assertEquals(Set.of("vm_node_can_report", "vm_node_can_read"), model.roles().get("vm_node.agent"));
        // the built-in roles of the type stand beside the declared ones
        assertEquals(Set.of("vm_node_can_read"), model.roles().get("vm_node.viewer"));
    }

    @Test
    public void theSystemsStaffRolesSpanEverythingOnIt() {
        AuthzModel model = generator.systemModel(systemServices());

        // the administrator holds everything in the model
        Set<String> everything = new TreeSet<>();
        model.permissions().forEach((type, permissions) -> permissions.forEach(permission -> everything.add(AuthzUtil.permissionName(type, permission))));
        assertEquals(everything, model.roles().get(AuthzConstants.SYSTEM_ADMIN_ROLE));
        // the maintainer holds the system's own permissions but those deciding who may act, and reads everything on it
        assertEquals(Set.of("system_can_read_cluster", "organization_can_read_members", "project_can_read", "vm_node_can_read"),
                     model.roles().get(AuthzConstants.SYSTEM_MAINTAINER_ROLE));
        // support reads the system and everything on it
        assertEquals(Set.of("system_can_read_cluster", "organization_can_read_members", "project_can_read", "vm_node_can_read"),
                     model.roles().get(AuthzConstants.SYSTEM_SUPPORT_ROLE));
        assertFalse(model.roles().containsKey("application.maintainer"));
    }

    @Test
    public void aDeclaredRoleBundlingAPermissionNoFunctionRequiresFails() {
        List<ServiceDefinition> services = List.of(service("VmNodeService", "vm_node", "system",
                                                           List.of(role("vm_node.registrar", "can_fly")),
                                                           function("register", "system", "vm_node", "can_register_node")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("can_fly"), e.getMessage());
    }

    @Test
    public void aDeclaredRoleTheModelAlreadyDefinesFails() {
        List<ServiceDefinition> services = List.of(service("VmNodeService", "vm_node", "system",
                                                           List.of(role("vm_node.editor", "can_register_node")),
                                                           function("register", "system", "vm_node", "can_register_node")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("vm_node.editor"), e.getMessage());
    }

    @Test
    public void twoServicesDeclaringOneRoleMustAgreeOnIt() {
        List<ServiceDefinition> agreeing = List.of(service("VmNodeService", "vm_node", "system",
                                                           List.of(role("vm_node.registrar", "can_register_node")),
                                                           function("register", "system", "vm_node", "can_register_node")),
                                                   service("VmNodeAdminService", "vm_node", "system",
                                                           List.of(role("vm_node.registrar", "can_register_node"))));
        List<ServiceDefinition> disagreeing = List.of(agreeing.getFirst(),
                                                      service("VmNodeAdminService", "vm_node", "system",
                                                              List.of(role("vm_node.registrar", "can_register_node", "can_read")),
                                                              function("findById", "vm_node", "vm_node", "can_read")));

        assertEquals(Set.of("vm_node_can_register_node"), generator.systemModel(agreeing).roles().get("vm_node.registrar"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(disagreeing));
        assertTrue(e.getMessage().contains("VmNodeAdminService"), e.getMessage());
    }

    @Test
    public void hashIdentifiesTheDefinition() {
        AuthzModel model = generator.systemModel(systemServices());
        AuthzModel same = generator.systemModel(systemServices());
        AuthzModel other = generator.systemModel(List.of(systemServices().getFirst()));

        assertEquals(model.hash(), same.hash());
        assertEquals(32, model.hash().length());
        assertNotEquals(model.hash(), other.hash());
    }

    @Test
    public void applicationModelRootsAtTheApplicationWithTheFixedEntityTypes() {
        AuthzModel model = generator.applicationModel(List.of(entitiesRepository()), List.of());

        assertEquals(applicationTypes(), typeNames(model));
        assertFalse(type(model, "application").get("relations").has("organization"));
        assertEquals(List.of("user", "group#member", "application#end_user", "tenant#member"),
                     directTypes(type(model, "tenant_role_binding"), "member"));
        // the application grants every role of its store, a tenant all but the application's, a definition within a
        // tenant only the definition's
        assertEquals(List.of("application_role", "entity_definition_role", "tenant_role"),
                     directTypes(type(model, "application_role_binding"), "role"));
        assertEquals(List.of("entity_definition_role", "tenant_role"), directTypes(type(model, "tenant_role_binding"), "role"));
        assertEquals(List.of("entity_definition_role"), directTypes(type(model, "tenant_definition_role_binding"), "role"));
        assertEquals(List.of("tenant_definition_role_binding"), directTypes(type(model, "tenant_definition"), "role_binding"));
        assertFalse(model.grantable("application.admin", "tenant"));
        // the application is placed in its own store by a wildcard tuple, and a definition under it is placed through it
        assertEquals(List.of("user:*"), directTypes(type(model, "application"), "placed"));
        assertEquals(List.of("application"), directTypes(type(model, "entity_definition"), "application"));
        assertEquals("ttu:application->placed", source(type(model, "entity_definition").get("relations").get("placed")));
        assertEquals(List.of("ttu:role_binding->entity_definition_can_edit", "computed:entity_definition_can_delete", "ttu:application->entity_definition_can_edit"),
                     children(type(model, "entity_definition"), "entity_definition_can_edit", "union"));
        assertEquals(Set.of("can_delete", "can_edit", "can_read"), model.permissions().get("entity_definition"));
        // the tenant and the application carry the rows' permissions, so a grant on either reaches every definition
        assertEquals(List.of("ttu:role_binding->entity_definition_can_read", "computed:entity_definition_can_edit", "ttu:application->entity_definition_can_read"),
                     children(type(model, "tenant"), "entity_definition_can_read", "union"));
        assertEquals(List.of("ttu:role_binding->entity_definition_can_read", "computed:entity_definition_can_edit"),
                     children(type(model, "application"), "entity_definition_can_read", "union"));
        // a definition within a tenant is reached from a binding on it, from the definition, and from the tenant
        // for a definition placed in the application
        JsonNode pair = type(model, "tenant_definition");
        assertEquals(List.of("entity_definition"), directTypes(pair, "definition"));
        assertEquals(List.of("tenant"), directTypes(pair, "tenant"));
        assertEquals("ttu:definition->placed", source(pair.get("relations").get("placed")));
        assertEquals(List.of("ttu:role_binding->entity_definition_can_read", "computed:entity_definition_can_edit",
                             "ttu:definition->entity_definition_can_read", "intersection:[ttu:tenant->entity_definition_can_read, computed:placed]"),
                     children(pair, "entity_definition_can_read", "union"));
        // the rows' permissions are the repository's, with the derived ladder
        assertEquals(Set.of("entity_definition_can_read"), model.roles().get("entity_definition.viewer"));
        assertEquals(Set.of("entity_definition_can_edit", "entity_definition_can_read"),
                     model.roles().get("entity_definition.editor"));
        assertTrue(model.roles().get("tenant.admin").contains("entity_definition_can_delete"));
        assertTrue(model.roles().get("application.admin").contains("entity_definition_can_delete"));
        assertFalse(model.roles().containsKey("tenant_definition.viewer"));
    }

    @Test
    public void anApplicationModelCarriesTheSystemServicesDeclaredOnTheTenant() {
        List<ServiceDefinition> system = List.of(service("TenantMemberService", "tenant", "application",
                                                           function("findMembers", "tenant", "tenant", "can_read_members")),
                                                   service("ApplicationService", "application", "organization",
                                                           function("findById", "application", "application", "can_read")),
                                                   service("ProjectService", "project", "application",
                                                           function("findById", "project", "project", "can_read")),
                                                   entitiesRepository());
        AuthzModel model = generator.applicationModel(system, List.of());

        assertEquals(applicationTypes(), typeNames(model));
        assertEquals(Set.of("can_read_members"), model.permissions().get("tenant"));
        assertEquals(Set.of("can_delete", "can_edit", "can_read"), model.permissions().get("entity_definition"));
        assertFalse(model.permissions().containsKey("application"));
        assertEquals(Set.of("tenant_can_read_members"), model.roles().get("tenant.viewer"));
        assertTrue(model.roles().get("tenant.admin").containsAll(Set.of("tenant_can_read_members", "entity_definition_can_delete")));
    }

    @Test
    public void aServiceOnTheDefinitionTypeAddsItsPermissionsToTheSystemsDefinition() {
        ServiceDefinition entities = service("JsonEntitiesRepository", "entity_definition", "application",
                                             function("findById", "entity_definition", "entity_definition", "can_read"));
        List<ServiceDefinition> system = new ArrayList<>(systemServices());
        system.add(entities);
        AuthzModel model = generator.systemModel(system);

        assertTrue(type(model, "entity_definition").get("relations").has("entity_definition_can_read"));
        assertTrue(type(model, "organization").get("relations").has("entity_definition_can_read"));
        assertTrue(model.roles().get("entity_definition.viewer").contains("entity_definition_can_read"));
    }

    @Test
    public void aParentNoServiceOrKernelDeclaresFails() {
        List<ServiceDefinition> services = List.of(service("WidgetService", "widget", "galaxy",
                                                           function("findById", "widget", "widget", "can_read")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("galaxy"));
    }

    @Test
    public void aSecondParentForTheSameTypeFails() {
        List<ServiceDefinition> services = List.of(service("ProjectService", "project", "application"),
                                                   service("OtherProjectService", "project", "organization"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("OtherProjectService"));
    }

    @Test
    public void aPermissionOfAnUndeclaredTypeFails() {
        List<ServiceDefinition> services = List.of(service("ProjectService", "project", "application",
                                                           function("audit", "project", "ledger", "can_read")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("ledger"));
    }

    @Test
    public void aPermissionNameLongerThanARelationAllowsFails() {
        List<ServiceDefinition> services = List.of(service("LongService", "a_very_long_resource_type_name_indeed", "application",
                                                           function("audit", "a_very_long_resource_type_name_indeed",
                                                                    "a_very_long_resource_type_name_indeed", "can_do_something_long")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.systemModel(services));

        assertTrue(e.getMessage().contains("50"));
    }

}
