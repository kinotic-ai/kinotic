package org.kinotic.authz.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.authz.api.model.EntityScope;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
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

    private static ServiceDefinition service(String name, String type, String parent, FunctionDefinition... functions) {
        return service(name, type, parent, List.of(), functions);
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

    private static List<ServiceDefinition> platformServices() {
        return List.of(service("ProjectService", "project", "application",
                               function("findById", "project", "project", "can_view"),
                               function("save", "project", "project", "can_edit"),
                               function("deleteById", "project", "project", "can_delete"),
                               function("deploy", "project", "project", "can_deploy", "can_view"),
                               function("count", "application", "project", "can_view")),
                       service("VmNodeService", "vm_node", "platform",
                               List.of(role("vm_node.registrar", "can_register_node"), role("vm_node.agent", "can_report", "can_view")),
                               function("register", "platform", "vm_node", "can_register_node"),
                               function("heartbeat", "vm_node", "vm_node", "can_report"),
                               function("findById", "vm_node", "vm_node", "can_view")),
                       service("MemberService", "organization", null,
                               function("findMembers", "organization", "organization", "can_view_members"),
                               function("removeMember", "organization", "organization", "can_manage_members")),
                       service("ClusterService", "platform", null,
                               function("getClusterInfo", "platform", "platform", "can_view_cluster"),
                               function("createMachine", "platform", "platform", "can_manage_machines")));
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
            if (child.has("tupleToUserset")) {
                JsonNode ttu = child.get("tupleToUserset");
                ret.add("ttu:" + ttu.get("tupleset").get("relation").asString() + "->"
                                + ttu.get("computedUserset").get("relation").asString());
            } else {
                ret.add("computed:" + child.get("computedUserset").get("relation").asString());
            }
        }
        return ret;
    }

    @Test
    public void platformModelHasTheKernelAndOneTypePerService() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals("1.1", model.definition().get("schema_version").asString());
        assertEquals(Set.of("user", "group", "role", "role_binding",
                            "application", "organization", "platform", "project", "tenant", "vm_node"),
                     typeNames(model));
        assertEquals(List.of("application"), directTypes(type(model, "project"), "application"));
        assertEquals(List.of("user"), directTypes(type(model, "organization"), "member"));
        assertEquals(List.of("user"), directTypes(type(model, "application"), "end_user"));
        assertEquals(List.of("user", "group#member"), directTypes(type(model, "group"), "member"));
    }

    @Test
    public void everyAncestorCarriesADescendantsPermissions() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:application->project_can_edit"),
                     children(type(model, "project"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:organization->project_can_edit"),
                     children(type(model, "application"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete", "ttu:platform->project_can_edit"),
                     children(type(model, "organization"), "project_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_edit", "computed:project_can_delete"),
                     children(type(model, "platform"), "project_can_edit", "union"));
        assertFalse(type(model, "tenant").get("relations").has("project_can_edit"));
    }

    @Test
    public void declaredAndDerivedImplicationsFormTheLattice() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(List.of("ttu:role_binding->project_can_view", "computed:project_can_deploy", "computed:project_can_edit",
                             "ttu:application->project_can_view"),
                     children(type(model, "project"), "project_can_view", "union"));
        assertEquals(List.of("ttu:role_binding->project_can_delete", "ttu:application->project_can_delete"),
                     children(type(model, "project"), "project_can_delete", "union"));
        // a single source is the relation itself, as the engine refuses a union of one
        JsonNode registerNode = type(model, "platform").get("relations").get("vm_node_can_register_node");
        assertFalse(registerNode.has("union"));
        assertEquals("role_binding", registerNode.get("tupleToUserset").get("tupleset").get("relation").asString());
        assertEquals("vm_node_can_register_node", registerNode.get("tupleToUserset").get("computedUserset").get("relation").asString());
    }

    @Test
    public void rolesBundleEveryPermissionAndBindingsNarrowThemToMembers() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "grant"));
        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "project_can_deploy"));
        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "vm_node_can_register_node"));
        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "platform_can_view_cluster"));
        assertEquals(List.of("role"), directTypes(type(model, "role_binding"), "role"));
        assertEquals(List.of("user", "group#member", "organization#member", "application#end_user", "tenant#member"),
                     directTypes(type(model, "role_binding"), "member"));
        assertEquals(List.of("computed:member", "ttu:role->project_can_deploy"),
                     children(type(model, "role_binding"), "project_can_deploy", "intersection"));
        assertEquals(List.of("role_binding"), directTypes(type(model, "project"), "role_binding"));
    }

    @Test
    public void catalogListsTheShortPermissionsOfEachType() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(Map.of("project", Set.of("can_delete", "can_deploy", "can_edit", "can_view"),
                            "vm_node", Set.of("can_register_node", "can_report", "can_view"),
                            "organization", Set.of("can_view_members", "can_manage_members"),
                            "platform", Set.of("can_view_cluster", "can_manage_machines")),
                     model.permissions());
    }

    @Test
    public void builtInRolesFollowTheTypesAndTheTree() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(Set.of("project_can_view"), model.roles().get("project.viewer"));
        assertEquals(Set.of("project_can_view", "project_can_edit", "project_can_deploy"), model.roles().get("project.editor"));
        assertEquals(Set.of("project_can_view", "project_can_edit", "project_can_deploy", "project_can_delete"), model.roles().get("project.admin"));
        // the admin of a container holds everything inside it and its own, the developer everything but the container's own
        assertEquals(Set.of("project_can_view", "project_can_edit", "project_can_deploy", "project_can_delete",
                            "organization_can_view_members", "organization_can_manage_members"),
                     model.roles().get(AuthzUtil.ORGANIZATION_ADMIN_ROLE));
        assertEquals(model.roles().get("project.admin"), model.roles().get("application.admin"));
        assertEquals(model.roles().get("project.admin"), model.roles().get(AuthzUtil.APPLICATION_DEVELOPER_ROLE));
        // a permission named can_view_<something> only reads, so the viewer holds it
        assertEquals(Set.of("organization_can_view_members"), model.roles().get("organization.viewer"));
        assertEquals(Set.of("organization_can_view_members", "organization_can_manage_members"), model.roles().get("organization.editor"));
        // a type with no reading permission has no viewer, and a role bundling nothing is not a role
        assertEquals(Set.of("platform_can_view_cluster", "platform_can_manage_machines"), model.roles().get("platform.editor"));
        assertFalse(model.roles().containsKey("tenant.viewer"));
        assertFalse(model.roles().containsKey("tenant.admin"));
    }

    @Test
    public void declaredRolesBundleTheirTypesPermissions() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(Set.of("vm_node_can_register_node"), model.roles().get("vm_node.registrar"));
        assertEquals(Set.of("vm_node_can_report", "vm_node_can_view"), model.roles().get("vm_node.agent"));
        // the built-in roles of the type stand beside the declared ones
        assertEquals(Set.of("vm_node_can_view"), model.roles().get("vm_node.viewer"));
    }

    @Test
    public void thePlatformsStaffRolesSpanEverythingOnIt() {
        AuthzModel model = generator.platformModel(platformServices());

        // the administrator holds everything in the model
        Set<String> everything = new java.util.TreeSet<>();
        model.permissions().forEach((type, permissions) -> permissions.forEach(permission -> everything.add(AuthzUtil.permissionName(type, permission))));
        assertEquals(everything, model.roles().get(AuthzUtil.PLATFORM_ADMIN_ROLE));
        // the operator holds the platform's own permissions and reads everything on it
        assertEquals(Set.of("platform_can_view_cluster", "platform_can_manage_machines",
                            "organization_can_view_members", "project_can_view", "vm_node_can_view"),
                     model.roles().get(AuthzUtil.PLATFORM_OPERATOR_ROLE));
        // support reads the platform and everything on it
        assertEquals(Set.of("platform_can_view_cluster", "organization_can_view_members", "project_can_view", "vm_node_can_view"),
                     model.roles().get(AuthzUtil.PLATFORM_SUPPORT_ROLE));
        assertFalse(model.roles().containsKey("application.operator"));
    }

    @Test
    public void aDeclaredRoleBundlingAPermissionNoFunctionRequiresFails() {
        List<ServiceDefinition> services = List.of(service("VmNodeService", "vm_node", "platform",
                                                           List.of(role("vm_node.registrar", "can_fly")),
                                                           function("register", "platform", "vm_node", "can_register_node")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("can_fly"), e.getMessage());
    }

    @Test
    public void aDeclaredRoleTheModelAlreadyDefinesFails() {
        List<ServiceDefinition> services = List.of(service("VmNodeService", "vm_node", "platform",
                                                           List.of(role("vm_node.editor", "can_register_node")),
                                                           function("register", "platform", "vm_node", "can_register_node")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("vm_node.editor"), e.getMessage());
    }

    @Test
    public void twoServicesDeclaringOneRoleMustAgreeOnIt() {
        List<ServiceDefinition> agreeing = List.of(service("VmNodeService", "vm_node", "platform",
                                                           List.of(role("vm_node.registrar", "can_register_node")),
                                                           function("register", "platform", "vm_node", "can_register_node")),
                                                   service("VmNodeAdminService", "vm_node", "platform",
                                                           List.of(role("vm_node.registrar", "can_register_node"))));
        List<ServiceDefinition> disagreeing = List.of(agreeing.getFirst(),
                                                      service("VmNodeAdminService", "vm_node", "platform",
                                                              List.of(role("vm_node.registrar", "can_register_node", "can_view")),
                                                              function("findById", "vm_node", "vm_node", "can_view")));

        assertEquals(Set.of("vm_node_can_register_node"), generator.platformModel(agreeing).roles().get("vm_node.registrar"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(disagreeing));
        assertTrue(e.getMessage().contains("VmNodeAdminService"), e.getMessage());
    }

    @Test
    public void hashIdentifiesTheDefinition() {
        AuthzModel model = generator.platformModel(platformServices());
        AuthzModel same = generator.platformModel(platformServices());
        AuthzModel other = generator.platformModel(List.of(platformServices().getFirst()));

        assertEquals(model.hash(), same.hash());
        assertEquals(32, model.hash().length());
        assertNotEquals(model.hash(), other.hash());
    }

    @Test
    public void applicationModelRootsAtTheApplicationAndAddsEntityTypes() {
        AuthzModel model = generator.applicationModel(List.of(), List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT),
                                                                         new EntityResource("catalog", EntityScope.APPLICATION)));

        assertEquals(Set.of("user", "group", "role", "role_binding", "application", "catalog", "invoice", "tenant"),
                     typeNames(model));
        assertFalse(type(model, "application").get("relations").has("organization"));
        assertEquals(List.of("user", "group#member", "application#end_user", "tenant#member"),
                     directTypes(type(model, "role_binding"), "member"));
        assertEquals(List.of("tenant"), directTypes(type(model, "invoice"), "tenant"));
        assertEquals(List.of("application"), directTypes(type(model, "catalog"), "application"));
        assertEquals(List.of("ttu:role_binding->invoice_can_edit", "computed:invoice_can_delete", "ttu:application->invoice_can_edit"),
                     children(type(model, "tenant"), "invoice_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->invoice_can_edit", "computed:invoice_can_delete"),
                     children(type(model, "application"), "invoice_can_edit", "union"));
        assertEquals(List.of("ttu:role_binding->invoice_can_search", "computed:invoice_can_read", "ttu:tenant->invoice_can_search"),
                     children(type(model, "invoice"), "invoice_can_search", "union"));
        assertEquals(Set.of("can_create", "can_delete", "can_edit", "can_read", "can_search"), model.permissions().get("invoice"));
        // a tenant carries the application's own entity types too, answered through the application above it
        assertEquals(List.of("ttu:role_binding->catalog_can_read", "computed:catalog_can_edit", "ttu:application->catalog_can_read"),
                     children(type(model, "tenant"), "catalog_can_read", "union"));
        assertTrue(model.roles().get("tenant.admin").containsAll(Set.of("invoice_can_delete", "catalog_can_delete")));
    }

    @Test
    public void anApplicationModelCarriesThePlatformServicesDeclaredOnTheTenant() {
        List<ServiceDefinition> platform = List.of(service("TenantMemberService", "tenant", "application",
                                                           function("findMembers", "tenant", "tenant", "can_view_members")),
                                                   service("ApplicationService", "application", "organization",
                                                           function("findById", "application", "application", "can_view")),
                                                   service("ProjectService", "project", "application",
                                                           function("findById", "project", "project", "can_view")));
        AuthzModel model = generator.applicationModel(platform, List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT)));

        assertEquals(Set.of("user", "group", "role", "role_binding", "application", "invoice", "tenant"), typeNames(model));
        assertEquals(Set.of("can_view_members"), model.permissions().get("tenant"));
        assertFalse(model.permissions().containsKey("application"));
        assertEquals(Set.of("tenant_can_view_members"), model.roles().get("tenant.viewer"));
        assertTrue(model.roles().get("tenant.admin").containsAll(Set.of("tenant_can_view_members", "invoice_can_delete")));
    }

    @Test
    public void aServiceWhoseTypeEachRequestNamesDeclaresNoType() {
        ServiceDefinition entities = service("JsonEntitiesRepository", "{entityDefinitionId}", "tenant",
                                             function("findById", "tenant", "{entityDefinitionId}", "can_read"));
        AuthzModel model = generator.applicationModel(List.of(), List.of(entities), List.of(new EntityResource("invoice", EntityScope.TENANT)));

        assertEquals(Set.of("user", "group", "role", "role_binding", "application", "invoice", "tenant"), typeNames(model));
    }

    @Test
    public void aParentNoServiceOrKernelDeclaresFails() {
        List<ServiceDefinition> services = List.of(service("WidgetService", "widget", "galaxy",
                                                           function("findById", "widget", "widget", "can_view")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("galaxy"));
    }

    @Test
    public void aSecondParentForTheSameTypeFails() {
        List<ServiceDefinition> services = List.of(service("ProjectService", "project", "application"),
                                                   service("OtherProjectService", "project", "organization"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("OtherProjectService"));
    }

    @Test
    public void aPermissionOfAnUndeclaredTypeFails() {
        List<ServiceDefinition> services = List.of(service("ProjectService", "project", "application",
                                                           function("audit", "project", "ledger", "can_view")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("ledger"));
    }

    @Test
    public void anEntityNamedLikeAServiceTypeFails() {
        List<ServiceDefinition> services = List.of(service("ProjectService", "project", "application"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> generator.applicationModel(List.of(), services, List.of(new EntityResource("project", EntityScope.APPLICATION))));

        assertTrue(e.getMessage().contains("project"));
    }

    @Test
    public void aPermissionNameLongerThanARelationAllowsFails() {
        List<ServiceDefinition> services = List.of(service("LongService", "a_very_long_resource_type_name_indeed", "application",
                                                           function("audit", "a_very_long_resource_type_name_indeed",
                                                                    "a_very_long_resource_type_name_indeed", "can_do_something_long")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generator.platformModel(services));

        assertTrue(e.getMessage().contains("50"));
    }

}
