package org.kinotic.idl.api.utils;

import java.util.Set;

/**
 * The names shared by everything that reads or writes an authorization declaration: the kernel types, the
 * relations, the built-in role levels and roles, and the permissions the system's own services declare.
 */
public final class AuthzConstants {

    /**
     * Marks a template reference to the calling participant's scope rather than to a parameter, as in
     * {@code {@organizationId}}.
     */
    public static final String SCOPE_REFERENCE_PREFIX = "@";

    /** The template references to the calling participant's scope: its organization, application and tenant. */
    public static final String ORGANIZATION_ID_REFERENCE = SCOPE_REFERENCE_PREFIX + "organizationId";
    public static final String APPLICATION_ID_REFERENCE = SCOPE_REFERENCE_PREFIX + "applicationId";
    public static final String TENANT_ID_REFERENCE = SCOPE_REFERENCE_PREFIX + "tenantId";
    public static final Set<String> SCOPE_REFERENCES = Set.of(ORGANIZATION_ID_REFERENCE, APPLICATION_ID_REFERENCE, TENANT_ID_REFERENCE);

    /** The type of the system itself, the root every organization sits under. */
    public static final String SYSTEM_TYPE = "system";

    /** The id of the one system object, so a check on the system needs no id from the request. */
    public static final String SYSTEM_OBJECT_ID = "kinotic";

    /** The one system object, {@code system:kinotic}. */
    public static final String SYSTEM_OBJECT = AuthzUtil.object(SYSTEM_TYPE, SYSTEM_OBJECT_ID);

    /** The kernel types every store has: the identities. */
    public static final String USER_TYPE = "user";
    public static final String GROUP_TYPE = "group";
    /**
     * The suffix of the type of a role homed at a resource type, {@code project_role}, which bundles only
     * permissions of that type and of the types inside it.
     */
    public static final String ROLE_TYPE_SUFFIX = "_role";
    /**
     * The suffix of the type of a binding attached to a resource type, {@code project_role_binding}, which grants
     * only roles homed at that type or inside it.
     */
    public static final String ROLE_BINDING_TYPE_SUFFIX = "_role_binding";

    /** The type an entity definition is in the system store, under its application. */
    public static final String ENTITY_DEFINITION_TYPE = "entity_definition";
    /**
     * The type of a definition's rows within one tenant, in an application's store: a definition and a tenant
     * paired, so a grant made on it reaches that tenant's rows of that definition alone.
     */
    public static final String TENANT_DEFINITION_TYPE = "tenant_definition";
    /** The relation of a definition's rows within a tenant to the definition. */
    public static final String DEFINITION_RELATION = "definition";
    /**
     * The relation everyone holds on an application in the application's own store, written when the store is
     * provisioned, and so on every definition placed under the application there: what a check on a definition's
     * rows within a tenant reads to tell a definition of this application from one of another.
     */
    public static final String PLACED_RELATION = "placed";

    /** The type a project is in the system store, under its application. */
    public static final String PROJECT_TYPE = "project";

    /** The kernel types of the identity scopes, each contained in the one before it. */
    public static final String ORGANIZATION_TYPE = "organization";
    public static final String APPLICATION_TYPE = "application";
    public static final String TENANT_TYPE = "tenant";

    /** The membership relation of an organization, a tenant or a group. */
    public static final String MEMBER_RELATION = "member";
    /** The membership relation of an application's end users. */
    public static final String END_USER_RELATION = "end_user";
    /** The relation of a binding to the role it grants. */
    public static final String ROLE_RELATION = "role";
    /** The relation of a resource to a binding attached to it. */
    public static final String ROLE_BINDING_RELATION = "role_binding";
    /** The relation a role holds for everyone, which a binding then narrows to its members. */
    public static final String GRANT_RELATION = "grant";
    /** Everyone, the user of every tuple that puts a permission in a role. */
    public static final String EVERYONE = USER_TYPE + ":*";

    /** The levels of the built-in roles every resource type gets: reading it, editing it, and everything. */
    public static final String VIEWER = "viewer";
    public static final String EDITOR = "editor";
    public static final String ADMIN = "admin";
    /** The level of the built-in role over everything inside an application but the application itself. */
    public static final String DEVELOPER = "developer";
    /** The levels of the system users' roles: running the system, and reading it. */
    public static final String MAINTAINER = "maintainer";
    public static final String SUPPORT = "support";
    /** The built-in role holding every permission of an organization and of everything inside it. */
    public static final String ORGANIZATION_ADMIN_ROLE = ORGANIZATION_TYPE + "." + ADMIN;
    /** The built-in role holding every permission of everything inside an application, and none of its own. */
    public static final String APPLICATION_DEVELOPER_ROLE = APPLICATION_TYPE + "." + DEVELOPER;
    /** The built-in role holding every permission of the system and of everything on it. */
    public static final String SYSTEM_ADMIN_ROLE = SYSTEM_TYPE + "." + ADMIN;
    /** The built-in role holding the system's own permissions and the reading permissions of everything on it. */
    public static final String SYSTEM_MAINTAINER_ROLE = SYSTEM_TYPE + "." + MAINTAINER;
    /** The built-in role holding the reading permissions of the system and of everything on it. */
    public static final String SYSTEM_SUPPORT_ROLE = SYSTEM_TYPE + "." + SUPPORT;
    /**
     * The declared role of an application's runtimes, which the contract service declares and a deployment grants
     * the runtime machines it provisions on the application.
     */
    public static final String APPLICATION_RUNTIME_ROLE = APPLICATION_TYPE + ".runtime";

    public static final String CAN_READ = "can_read";
    public static final String CAN_EDIT = "can_edit";
    public static final String CAN_DELETE = "can_delete";
    /**
     * The permissions the system's own services declare, beside the derived verbs: each named once here, so a
     * declaration, a grant, a check and a test name one thing.
     */
    public static final String CAN_MANAGE_ACCESS = "can_manage_access";
    public static final String CAN_MANAGE_CLUSTER = "can_manage_cluster";
    public static final String CAN_MANAGE_DEPLOYMENTS = "can_manage_deployments";
    public static final String CAN_MANAGE_INTEGRATIONS = "can_manage_integrations";
    public static final String CAN_MANAGE_MACHINES = "can_manage_machines";
    public static final String CAN_MANAGE_MEMBERS = "can_manage_members";
    public static final String CAN_MANAGE_WORKLOADS = "can_manage_workloads";
    public static final String CAN_REGISTER_NODE = "can_register_node";
    public static final String CAN_REGISTER_SERVICES = "can_register_services";
    public static final String CAN_REPORT = "can_report";
    public static final String CAN_READ_ACCESS = "can_read_access";
    public static final String CAN_READ_CLUSTER = "can_read_cluster";
    public static final String CAN_READ_DEPLOYMENTS = "can_read_deployments";
    public static final String CAN_READ_INTEGRATIONS = "can_read_integrations";
    public static final String CAN_READ_JOBS = "can_read_jobs";
    public static final String CAN_READ_MACHINES = "can_read_machines";
    public static final String CAN_READ_MEMBERS = "can_read_members";
    public static final String CAN_READ_TELEMETRY = "can_read_telemetry";
    public static final String CAN_READ_WORKLOADS = "can_read_workloads";
    /**
     * The permissions deciding who may act: granting roles, issuing machine credentials and admitting members. A
     * holder of one can give itself any other, so only an administrator holds them.
     */
    public static final Set<String> AUTHORITY_PERMISSIONS = Set.of(CAN_MANAGE_ACCESS, CAN_MANAGE_MACHINES, CAN_MANAGE_MEMBERS);
    /** The resolver deciding a named query's permission from its statement. */
    public static final String NAMED_QUERY_PERMISSION_RESOLVER = "named_query";
    /** The longest relation name OpenFGA accepts; a typed permission name must fit it. */
    public static final int MAX_RELATION_NAME_LENGTH = 50;

    private AuthzConstants() {
    }
}
