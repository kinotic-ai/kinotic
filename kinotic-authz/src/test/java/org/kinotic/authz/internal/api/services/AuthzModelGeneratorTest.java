package org.kinotic.authz.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.authz.api.model.EntityScope;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
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
        ServiceDefinition ret = new ServiceDefinition()
                .setNamespace("org.kinotic.test")
                .setName(name);
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(type).setParent(parent)));
        for (FunctionDefinition function : functions) {
            ret.addFunction(function);
        }
        return ret;
    }

    private static FunctionDefinition function(String name,
                                               String resource,
                                               String permissionResource,
                                               String permission,
                                               String... implies) {
        FunctionDefinition ret = new FunctionDefinition().setName(name);
        ret.setDecorators(List.of(new AuthzCheckC3Decorator()
                                          .setResource(resource)
                                          .setObjectId("{id}")
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
                               function("register", "platform", "vm_node", "can_register_node")));
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
        assertEquals(List.of("ttu:role_binding->vm_node_can_register_node"),
                     children(type(model, "platform"), "vm_node_can_register_node", "union"));
    }

    @Test
    public void rolesBundleEveryPermissionAndBindingsNarrowThemToMembers() {
        AuthzModel model = generator.platformModel(platformServices());

        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "grant"));
        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "project_can_deploy"));
        assertEquals(List.of("user:*"), directTypes(type(model, "role"), "vm_node_can_register_node"));
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
                            "vm_node", Set.of("can_register_node")),
                     model.permissions());
    }

    @Test
    public void hashIdentifiesTheDefinition() {
        AuthzModel model = generator.platformModel(platformServices());
        AuthzModel same = generator.platformModel(platformServices());
        AuthzModel other = generator.platformModel(List.of(platformServices().getFirst()));

        assertEquals(model.hash(), same.hash());
        assertEquals(64, model.hash().length());
        assertNotEquals(model.hash(), other.hash());
    }

    @Test
    public void applicationModelRootsAtTheApplicationAndAddsEntityTypes() {
        AuthzModel model = generator.applicationModel(List.of(), List.of(new EntityResource("invoice", EntityScope.TENANT),
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
                                                  () -> generator.applicationModel(services, List.of(new EntityResource("project", EntityScope.APPLICATION))));

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
