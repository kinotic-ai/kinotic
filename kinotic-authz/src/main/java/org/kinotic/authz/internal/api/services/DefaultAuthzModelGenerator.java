package org.kinotic.authz.internal.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
public class DefaultAuthzModelGenerator implements AuthzModelGenerator {

    private static final String USER = AuthzConstants.USER_TYPE;
    private static final String GROUP = AuthzConstants.GROUP_TYPE;
    private static final String ORGANIZATION = AuthzConstants.ORGANIZATION_TYPE;
    private static final String APPLICATION = AuthzConstants.APPLICATION_TYPE;
    private static final String TENANT = AuthzConstants.TENANT_TYPE;
    private static final String MEMBER = AuthzConstants.MEMBER_RELATION;
    private static final String END_USER = AuthzConstants.END_USER_RELATION;
    private static final String ROLE = AuthzConstants.ROLE_RELATION;
    private static final String ROLE_BINDING = AuthzConstants.ROLE_BINDING_RELATION;

    private static final String SCHEMA_VERSION = "1.1";
    private static final String ENTITY_DEFINITION = AuthzConstants.ENTITY_DEFINITION_TYPE;
    private static final String TENANT_DEFINITION = AuthzConstants.TENANT_DEFINITION_TYPE;
    private static final String DEFINITION = AuthzConstants.DEFINITION_RELATION;
    private static final String PLACED = AuthzConstants.PLACED_RELATION;
    /**
     * The type of the parts of a role that names definitions. A part holds a permission for everyone under
     * {@link #FOR_DEFINITION}, and its role holds the part under {@link #IN_PART}.
     */
    static final String ROLE_PART = "role_part";
    /**
     * The relation a role holds each of its parts by.
     */
    static final String PART = "part";
    /**
     * The condition a part of a role bundles a permission of an entity definition under: the tuple carries the
     * definitions the part names as the keys of {@link #NAMED_DEFINITIONS}, the check {@link #CHECKED_DEFINITION}.
     */
    static final String FOR_DEFINITION = "for_definition";
    static final String CHECKED_DEFINITION = "definition";
    static final String NAMED_DEFINITIONS = "definitions";
    /**
     * The condition a role holds a part under, true of the one part a checked definition falls in: the tuple
     * carries the part's {@link #PART_INDEX} among the role's {@link #PART_COUNT}, the check
     * {@link #CHECKED_DEFINITION_HASH}.
     */
    static final String IN_PART = "in_part";
    static final String CHECKED_DEFINITION_HASH = "definition_hash";
    static final String PART_INDEX = "part";
    static final String PART_COUNT = "parts";
    /**
     * The type whose objects are named by role ids. One holds every binding of its role under {@link #BINDING},
     * and its {@link #HOLDER} is everyone granted the role on anything in the store, so listing the objects a user
     * is the holder of says which roles the user holds.
     */
    static final String ROLE_HOLDERS = "role_holders";
    static final String BINDING = "binding";
    static final String HOLDER = "holder";
    /**
     * The condition a resource holds a binding under, true of the bindings whose role the caller holds: the tuple
     * carries the binding's role as {@link #BOUND_ROLE}, the check the roles the caller holds as the keys of
     * {@link #HELD_ROLES}. A check so reads the bindings of the roles its caller holds, however many roles are
     * granted on the resource; the binding still decides whether the caller is a member of it.
     */
    static final String ROLE_HELD = "role_held";
    static final String BOUND_ROLE = "role";
    static final String HELD_ROLES = "held";
    private static final String SERVICES = "service";
    private static final String PERMISSION = "permission";
    /**
     * The permission standing for every permission of an application's own services, which an application's store
     * has no relation for. A role holds it for all of them, or under {@link #FOR_DEFINITION} for the ones its parts
     * name by their model names, and a check names the one it asks about as {@link #CHECKED_DEFINITION}.
     */
    static final String SERVICE_PERMISSION = AuthzUtil.permissionName(SERVICES, PERMISSION);
    // the types a model defines beside the resource types, which a service's type would define a second time
    private static final Set<String> FIXED_TYPES = Set.of(USER, GROUP, TENANT_DEFINITION, ROLE_PART, ROLE_HOLDERS);
    // the types an application's store places a resource of; any other type in it is one of the application's services
    private static final Set<String> APPLICATION_TYPES = AuthzModelGenerator.APPLICATION_TYPES;
    // who a binding may be made to in an application's store, and in the system's, which also has organizations
    private static final List<String> APPLICATION_MEMBER_TYPES = List.of(USER, GROUP + "#" + MEMBER, APPLICATION + "#" + END_USER, TENANT + "#" + MEMBER);
    private static final List<String> SYSTEM_MEMBER_TYPES = List.of(USER, GROUP + "#" + MEMBER, ORGANIZATION + "#" + MEMBER,
                                                                    APPLICATION + "#" + END_USER, TENANT + "#" + MEMBER);

    @Override
    public AuthzModel systemModel(Collection<ServiceDefinition> services) {
        Map<String, ResourceType> kernel = new LinkedHashMap<>();
        kernel.put(AuthzConstants.SYSTEM_TYPE, new ResourceType(AuthzConstants.SYSTEM_TYPE, null));
        ResourceType organization = new ResourceType(ORGANIZATION, AuthzConstants.SYSTEM_TYPE);
        organization.memberships.put(MEMBER, Set.of(USER));
        kernel.put(ORGANIZATION, organization);
        ResourceType application = new ResourceType(APPLICATION, ORGANIZATION);
        application.memberships.put(END_USER, Set.of(USER));
        kernel.put(APPLICATION, application);
        kernel.put(TENANT, tenant());
        ResourceTypes types = declare(kernel, services);

        // an organization and a tenant are trust boundaries, so the system grants none of an organization's roles
        // and an organization none of a tenant's
        Map<String, Set<String>> bindable = bindable(types, Set.of(ORGANIZATION, TENANT));
        Map<String, Set<String>> roles = typeRoles(types);
        staffRoles(roles, types);
        declaredRoles(roles, types);
        return model(types, typeDefinitions(types, bindable, SYSTEM_MEMBER_TYPES), bindable, roles, Map.of());
    }

    @Override
    public AuthzModel applicationModel(Collection<ServiceDefinition> systemServices, Collection<ServiceDefinition> services) {
        List<ServiceDefinition> all = new ArrayList<>();
        for (ServiceDefinition service : systemServices) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            // the system's services an application's users call: the tenant's, and the entities repository
            // declared on the definition, whose permissions are the rows'
            if (resource != null && (TENANT.equals(resource.getResourceType()) || ENTITY_DEFINITION.equals(resource.getResourceType()))) {
                all.add(service);
            }
        }
        all.addAll(services);

        Map<String, ResourceType> kernel = new LinkedHashMap<>();
        ResourceType application = new ResourceType(APPLICATION, null);
        application.memberships.put(END_USER, Set.of(USER));
        // held by everyone through the one wildcard tuple the store is provisioned with, so a definition
        // under the application is told from one of another application by reading it through the definition
        application.memberships.put(PLACED, Set.of(AuthzConstants.EVERYONE));
        kernel.put(APPLICATION, application);
        // the definition the request names, whose permissions the entities repository declares; placed is held
        // by everyone for a definition under the application, by no one for one this store does not hold
        ResourceType definition = new ResourceType(ENTITY_DEFINITION, APPLICATION);
        definition.inherited.put(PLACED, APPLICATION);
        // a definition's permissions are its rows', so a role of the application says which of them its holders
        // have on which definitions: reading one definition's rows gives neither writing them nor another's
        definition.granular = true;
        kernel.put(ENTITY_DEFINITION, definition);
        kernel.put(TENANT, tenant());
        ResourceTypes declared = declare(kernel, all);

        // The store places an application, its tenants and its definitions, and the engine has a type for each. A
        // type of the application's own services has no resource in the store, so its permissions are names a role
        // holds under one relation: the model is the same however many services the application has
        Map<String, ResourceType> placed = new LinkedHashMap<>();
        Map<String, Set<String>> servicePermissions = new TreeMap<>();
        for (ResourceType type : declared.declared()) {
            if (APPLICATION_TYPES.contains(type.name)) {
                placed.put(type.name, type);
            } else {
                for (String permission : type.permissions.keySet()) {
                    servicePermissions.put(AuthzUtil.permissionName(type.name, permission), type.modelNames(type.held(permission)));
                }
            }
        }
        // a role's permissions name the relation beside the services' permissions, so none of those may be named as it is
        if (servicePermissions.containsKey(SERVICE_PERMISSION)) {
            throw new IllegalArgumentException("A permission of a service is named '" + SERVICE_PERMISSION
                                                       + "', which names every permission of the application's services");
        }
        ResourceType ofServices = new ResourceType(SERVICES, APPLICATION);
        ofServices.granular = true;
        ofServices.addPermission(PERMISSION, List.of());
        ResourceTypes types = new ResourceTypes(placed);
        types.carryUndeclared(APPLICATION, ofServices);
        types.carryUndeclared(TENANT, ofServices);
        // a tenant is a slice of its application, so it carries every type the application holds, the ones declared
        // beside it too: a check made on a caller's tenant answers for any of them, through the tenant's own grants
        // and the application's above it
        types.carry(TENANT, ENTITY_DEFINITION);
        for (ResourceType type : declared.declared()) {
            if (!APPLICATION.equals(type.name)) {
                declared.carry(TENANT, type.name);
            }
        }

        // the store has no trust boundary, and a role is granted on what the store places; a definition within a
        // tenant grants the definition's roles
        Map<String, Set<String>> bindable = bindable(declared, Set.of());
        bindable.keySet().retainAll(placed.keySet());
        bindable.put(TENANT_DEFINITION, Set.of(ENTITY_DEFINITION));
        // a role homed at a type of the application's services is held as a role of the tenant, so the engine's
        // bindings name the placed homes alone
        Map<String, Set<String>> roleTypes = new TreeMap<>();
        bindable.forEach((type, homes) -> {
            Set<String> typed = new TreeSet<>(homes);
            typed.retainAll(placed.keySet());
            roleTypes.put(type, typed);
        });
        ArrayNode typeDefinitions = typeDefinitions(types, roleTypes, APPLICATION_MEMBER_TYPES);
        typeDefinitions.add(rolePartType(List.of(definition, ofServices)));
        typeDefinitions.add(roleBindingType(TENANT_DEFINITION, roleTypes.get(TENANT_DEFINITION), definition.permissionNames(), APPLICATION_MEMBER_TYPES));
        typeDefinitions.add(tenantDefinitionType(definition));
        Map<String, Set<String>> roles = typeRoles(types);
        // a tenant's administrator decides who is in the tenant and what they hold there; the rows are held through
        // the roles it grants, so a definition the application keeps from its users is kept from the administrator
        String tenantAdmin = AuthzUtil.roleId(TENANT, AuthzConstants.ADMIN);
        roles.remove(tenantAdmin);
        role(roles, tenantAdmin, types.get(TENANT).permissionNames());
        typeRoles(declared).forEach((id, permissions) -> {
            if (!placed.containsKey(AuthzUtil.roleHome(id))) {
                roles.put(id, permissions);
            }
        });
        declaredRoles(roles, declared);
        return model(declared, typeDefinitions, bindable, roles, servicePermissions);
    }

    // The tenant every store has, with the members the identity model writes
    private static ResourceType tenant() {
        ResourceType ret = new ResourceType(TENANT, APPLICATION);
        ret.memberships.put(MEMBER, Set.of(USER));
        return ret;
    }

    // The kernel's types with the types, permissions and roles the services declare
    private static ResourceTypes declare(Map<String, ResourceType> types, Collection<ServiceDefinition> services) {
        declareServiceTypes(services, types);
        declarePermissions(services, types);
        deriveLattice(types);
        validate(types);
        return new ResourceTypes(types);
    }

    // The user and the group, then each resource type with the type of its roles and of its bindings
    private static ArrayNode typeDefinitions(ResourceTypes types, Map<String, Set<String>> bindable, List<String> memberTypes) {
        ArrayNode ret = JsonNodeFactory.instance.arrayNode();
        ret.add(typeDefinition(USER, Map.of()));
        ret.add(typeDefinition(GROUP, Map.of(MEMBER, direct(List.of(USER, GROUP + "#" + MEMBER)))));
        for (ResourceType type : types.sorted()) {
            List<ResourceType> carried = types.carriedBy(type.name);
            ret.add(roleType(type.name, carried));
            ret.add(roleBindingType(type.name, bindable.get(type.name), permissionNames(carried), memberTypes));
            ret.add(resourceType(type, carried));
        }
        return ret;
    }

    private static AuthzModel model(ResourceTypes types,
                                    ArrayNode typeDefinitions,
                                    Map<String, Set<String>> bindable,
                                    Map<String, Set<String>> roles,
                                    Map<String, Set<String>> servicePermissions) {
        typeDefinitions.add(roleHoldersType(typeDefinitions));
        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("schema_version", SCHEMA_VERSION);
        definition.set("type_definitions", typeDefinitions);

        Map<String, Set<String>> catalog = new TreeMap<>();
        Map<String, String> parents = new TreeMap<>();
        for (ResourceType type : types.declared()) {
            if (!type.permissions.isEmpty()) {
                catalog.put(type.name, new TreeSet<>(type.permissions.keySet()));
            }
            if (type.parent != null) {
                parents.put(type.name, type.parent);
            }
        }
        ObjectNode conditions = definition.putObject("conditions");
        conditions.set(ROLE_HELD, roleHeldCondition());
        if (types.declared().stream().anyMatch(type -> type.granular)) {
            conditions.setAll(definitionConditions());
        }
        return new AuthzModel(definition, ModelHash.of(definition), catalog, roles, bindable, parents, servicePermissions);
    }

    private static ObjectNode roleHeldCondition() {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ret.put("name", ROLE_HELD);
        ret.put("expression", BOUND_ROLE + " in " + HELD_ROLES);
        ObjectNode parameters = ret.putObject("parameters");
        parameters.putObject(BOUND_ROLE).put("type_name", "TYPE_NAME_STRING");
        ObjectNode held = parameters.putObject(HELD_ROLES);
        held.put("type_name", "TYPE_NAME_MAP");
        held.putArray("generic_types").addObject().put("type_name", "TYPE_NAME_BOOL");
        return ret;
    }

    // IN_PART holds for the part of a role the checked definition's hash falls in, and FOR_DEFINITION for the tuple
    // of that part naming the definition
    private static ObjectNode definitionConditions() {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ObjectNode inPart = ret.putObject(IN_PART);
        inPart.put("name", IN_PART);
        inPart.put("expression", CHECKED_DEFINITION_HASH + " % " + PART_COUNT + " == " + PART_INDEX);
        ObjectNode partParameters = inPart.putObject("parameters");
        for (String parameter : List.of(CHECKED_DEFINITION_HASH, PART_COUNT, PART_INDEX)) {
            partParameters.putObject(parameter).put("type_name", "TYPE_NAME_INT");
        }
        ObjectNode forDefinition = ret.putObject(FOR_DEFINITION);
        forDefinition.put("name", FOR_DEFINITION);
        forDefinition.put("expression", CHECKED_DEFINITION + " in " + NAMED_DEFINITIONS);
        ObjectNode parameters = forDefinition.putObject("parameters");
        parameters.putObject(CHECKED_DEFINITION).put("type_name", "TYPE_NAME_STRING");
        // a map, which the engine looks a key up in at one cost however many definitions the tuple names; a list
        // is scanned, and the engine stops evaluating a condition past a fixed cost
        ObjectNode named = parameters.putObject(NAMED_DEFINITIONS);
        named.put("type_name", "TYPE_NAME_MAP");
        named.putArray("generic_types").addObject().put("type_name", "TYPE_NAME_BOOL");
        return ret;
    }

    /**
     * The home types of the roles a binding on each resource type may grant: the type itself and the types it
     * carries, except those across a trust boundary below it.
     */
    private static Map<String, Set<String>> bindable(ResourceTypes types, Set<String> boundaries) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (ResourceType type : types.declared()) {
            Set<String> homes = new TreeSet<>();
            for (ResourceType home : types.carriedBy(type.name)) {
                if (withinBoundary(home.name, type.name, types, boundaries)) {
                    homes.add(home.name);
                }
            }
            ret.put(type.name, homes);
        }
        return ret;
    }

    // Whether the path up from the home to the type it is bound on crosses no boundary; a home carried by no
    // parent path, as a tenant carries the application's entity types in an application's store, crosses none
    private static boolean withinBoundary(String home, String boundOn, ResourceTypes types, Set<String> boundaries) {
        boolean ret = true;
        String at = home;
        while (at != null && !at.equals(boundOn)) {
            if (boundaries.contains(at)) {
                ret = false;
                at = null;
            } else {
                at = types.get(at).parent;
            }
        }
        return ret;
    }

    // The model names of every permission of the given types
    private static Set<String> permissionNames(Collection<ResourceType> types) {
        Set<String> ret = new TreeSet<>();
        types.forEach(type -> ret.addAll(type.permissionNames()));
        return ret;
    }

    // The model names of the reading permissions of the given types
    private static Set<String> readingNames(Collection<ResourceType> types) {
        Set<String> ret = new TreeSet<>();
        types.forEach(type -> ret.addAll(type.readingNames()));
        return ret;
    }

    /**
     * The roles every type has: a viewer of its reading permissions, an editor of all but deleting and deciding who
     * may act, and an admin of everything on it and inside it. A role that would bundle nothing is not a role.
     */
    private static Map<String, Set<String>> typeRoles(ResourceTypes types) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (ResourceType type : types.declared()) {
            // deciding who may act is an administrator's: managing access would let an editor grant itself any role,
            // and managing machines would let it sign in as one holding more than it does. A permission implying
            // one of them, or deleting, holds it too, so it is the administrator's as well
            Set<String> administering = new HashSet<>(AuthzConstants.AUTHORITY_PERMISSIONS);
            administering.add(AuthzConstants.CAN_DELETE);
            Set<String> editing = type.modelNames(type.permissions.keySet().stream()
                                                      .filter(permission -> !type.holdsAny(permission, administering))
                                                      .toList());
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.VIEWER), type.readingNames());
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.EDITOR), editing);
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.ADMIN), permissionNames(types.carriedBy(type.name)));
        }
        return ret;
    }

    /**
     * The roles of the system's staff and of an application's developers: the application developer, everything
     * inside an application but the application's own; the system maintainer, the system's own permissions but
     * those deciding who may act, and the reading ones of everything on it; and the system support, the reading
     * permissions of the system and of everything on it.
     */
    private static void staffRoles(Map<String, Set<String>> roles, ResourceTypes types) {
        ResourceType system = types.get(AuthzConstants.SYSTEM_TYPE);
        // the maintainer runs the system but never decides who may act on it, which would let it make itself
        // an administrator
        Set<String> maintaining = system.modelNames(system.permissions.keySet().stream()
                                                          .filter(permission -> !system.holdsAny(permission, AuthzConstants.AUTHORITY_PERMISSIONS))
                                                          .toList());
        maintaining.addAll(readingNames(types.inside(system.name)));
        role(roles, AuthzConstants.APPLICATION_DEVELOPER_ROLE, permissionNames(types.inside(APPLICATION)));
        role(roles, AuthzConstants.SYSTEM_MAINTAINER_ROLE, maintaining);
        role(roles, AuthzConstants.SYSTEM_SUPPORT_ROLE, readingNames(types.carriedBy(system.name)));
    }

    // The roles the types' services declare, each bundling permissions of its type
    private static void declaredRoles(Map<String, Set<String>> roles, ResourceTypes types) {
        for (ResourceType type : types.declared()) {
            for (Map.Entry<String, Set<String>> declared : type.declaredRoles.entrySet()) {
                if (roles.containsKey(declared.getKey())) {
                    throw new IllegalArgumentException("Role '" + declared.getKey() + "' is declared by a service of '"
                                                               + type.name + "', but the model already defines it");
                }
                roles.put(declared.getKey(), type.modelNames(declared.getValue()));
            }
        }
    }

    private static void role(Map<String, Set<String>> roles, String id, Set<String> permissions) {
        if (!permissions.isEmpty()) {
            roles.put(id, permissions);
        }
    }

    private static void declareServiceTypes(Collection<ServiceDefinition> services, Map<String, ResourceType> types) {
        // a type is declared by each of its services and its parent by any of them, so every service is read for
        // the parents before a type is made, whichever order the services come in
        Map<String, String> parents = new HashMap<>();
        for (ServiceDefinition service : services) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            if (resource != null && resource.getParent() != null) {
                ResourceType kernel = types.get(resource.getResourceType());
                String parent = kernel != null
                        ? kernel.parent
                        : parents.computeIfAbsent(resource.getResourceType(), type -> resource.getParent());
                // the kernel's parent, or another service's, is the one in the tree; a second parent would fork it
                if (!resource.getParent().equals(parent)) {
                    throw new IllegalArgumentException("Service " + service.getQualifiedName() + " declares the parent of '"
                                                               + resource.getResourceType() + "' as '" + resource.getParent()
                                                               + "', but it is '" + parent + "'");
                }
            }
        }
        for (ServiceDefinition service : services) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            if (resource != null) {
                ResourceType type = types.computeIfAbsent(resource.getResourceType(), name -> new ResourceType(name, parents.get(name)));
                for (AuthzRoleDeclaration role : resource.getRoles()) {
                    Set<String> permissions = new LinkedHashSet<>(role.getPermissions());
                    Set<String> declared = type.declaredRoles.putIfAbsent(role.getId(), permissions);
                    // two services of one type may declare the same role, as long as they agree on it
                    if (declared != null && !declared.equals(permissions)) {
                        throw new IllegalArgumentException("Service " + service.getQualifiedName() + " declares the role '"
                                                                   + role.getId() + "' bundling " + permissions
                                                                   + ", but another service declares it bundling " + declared);
                    }
                }
            }
        }
    }

    private static void declarePermissions(Collection<ServiceDefinition> services, Map<String, ResourceType> types) {
        for (ServiceDefinition service : services) {
            for (FunctionDefinition function : service.getFunctions()) {
                AuthzCheckC3Decorator check = function.findDecorator(AuthzCheckC3Decorator.class);
                if (check != null && !check.isUnchecked()) {
                    ResourceType type = types.get(check.getPermissionResource());
                    if (type == null) {
                        throw new IllegalArgumentException("Function " + function.getName() + " of " + service.getQualifiedName()
                                                                   + " requires a permission of '" + check.getPermissionResource()
                                                                   + "', which no service or entity definition declares");
                    }
                    if (check.getPermissionResolver() == null) {
                        type.addPermission(check.getPermission(), check.getImplies());
                    } else {
                        for (AuthzPermissionChoice choice : check.getPossiblePermissions()) {
                            type.addPermission(choice.getPermission(), List.of());
                        }
                    }
                }
            }
        }
    }

    // the derived verbs form a ladder: deleting implies editing, editing implies reading, wherever both exist; a
    // granular type's stand alone
    private static void deriveLattice(Map<String, ResourceType> types) {
        for (ResourceType type : types.values()) {
            if (!type.granular) {
                implyIfBoth(type, AuthzConstants.CAN_DELETE, AuthzConstants.CAN_EDIT);
                implyIfBoth(type, AuthzConstants.CAN_EDIT, AuthzConstants.CAN_READ);
            }
        }
    }

    private static void implyIfBoth(ResourceType type, String stronger, String weaker) {
        if (type.permissions.containsKey(stronger) && type.permissions.containsKey(weaker)) {
            type.permissions.get(stronger).add(weaker);
        }
    }

    private static void validate(Map<String, ResourceType> types) {
        // walking an ancestor chain follows every parent up the tree, so each must exist before any chain is walked
        for (ResourceType type : types.values()) {
            if (type.parent != null && !types.containsKey(type.parent)) {
                throw new IllegalArgumentException("Resource type '" + type.name + "' declares the parent '" + type.parent
                                                           + "', which this model does not have");
            }
        }
        Map<String, String> permissionTypes = new HashMap<>();
        for (ResourceType type : types.values()) {
            if (FIXED_TYPES.contains(type.name)) {
                throw new IllegalArgumentException("Resource type '" + type.name + "' is one the model defines itself");
            }
            // the model names a type's roles and bindings by suffixing it, so a type named like one would collide
            if (type.name.endsWith(AuthzConstants.ROLE_TYPE_SUFFIX) || type.name.endsWith(AuthzConstants.ROLE_BINDING_TYPE_SUFFIX)) {
                throw new IllegalArgumentException("Resource type '" + type.name + "' ends with '" + AuthzConstants.ROLE_TYPE_SUFFIX
                                                           + "' or '" + AuthzConstants.ROLE_BINDING_TYPE_SUFFIX
                                                           + "', which name the model's roles and bindings");
            }
            Set<String> seen = new HashSet<>();
            for (String ancestor = type.name; ancestor != null; ancestor = types.get(ancestor).parent) {
                if (!seen.add(ancestor)) {
                    throw new IllegalArgumentException("Resource type '" + type.name + "' is contained in itself through '"
                                                               + ancestor + "'");
                }
            }
            for (Map.Entry<String, Set<String>> permission : type.permissions.entrySet()) {
                String name = AuthzUtil.permissionName(type.name, permission.getKey());
                if (name.length() > AuthzConstants.MAX_RELATION_NAME_LENGTH) {
                    throw new IllegalArgumentException("Permission '" + name + "' is longer than the "
                                                               + AuthzConstants.MAX_RELATION_NAME_LENGTH + " characters a relation name may have");
                }
                // a role and a resource carry the permissions of several types as relations named alike, so two
                // types' permissions of one name would be one relation: a_b_c is both b_c of a and c of a_b
                String other = permissionTypes.putIfAbsent(name, type.name);
                if (other != null) {
                    throw new IllegalArgumentException("Permission '" + permission.getKey() + "' of '" + type.name
                                                               + "' and a permission of '" + other + "' are both named '"
                                                               + name + "' in the model");
                }
                for (String implied : permission.getValue()) {
                    if (!type.permissions.containsKey(implied)) {
                        throw new IllegalArgumentException("Permission '" + name + "' implies '" + implied
                                                                   + "', which '" + type.name + "' does not have");
                    }
                    // the viewer and the reading roles bundle every reading permission, so one implying more than
                    // reading would let them do it
                    if (AuthzUtil.isReading(permission.getKey()) && !AuthzUtil.isReading(implied)) {
                        throw new IllegalArgumentException("Permission '" + name + "' is a reading permission, but implies '"
                                                                   + implied + "', which is not one");
                    }
                }
            }
            for (Map.Entry<String, Set<String>> role : type.declaredRoles.entrySet()) {
                for (String permission : role.getValue()) {
                    if (!type.permissions.containsKey(permission)) {
                        throw new IllegalArgumentException("Role '" + role.getKey() + "' bundles '" + permission
                                                                   + "', which no function of '" + type.name + "' requires");
                    }
                }
            }
        }
    }

    /**
     * A role homed at a type is a bundle of the permissions of that type and of the types it carries: for each,
     * the role holds it for everyone or for nobody, which the binding then narrows to its members, and it holds
     * every permission a permission it bundles implies. A permission of a granular type is also held on the
     * resources the role's parts name. The engine refuses a role bundling a permission from above its home.
     */
    private static ObjectNode roleType(String home, List<ResourceType> carried) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        if (carried.stream().anyMatch(about -> about.granular)) {
            relate(relations, PART, directUnder(ROLE_PART, IN_PART));
        }
        for (ResourceType about : carried) {
            for (Map.Entry<String, Set<String>> permission : new TreeMap<>(about.permissions).entrySet()) {
                ObjectNode bundled = direct(List.of(USER + ":*"));
                List<ObjectNode> sources = implying(about, permission.getKey());
                if (about.granular) {
                    sources.add(tupleToUserset(PART, AuthzUtil.permissionName(about.name, permission.getKey())));
                }
                ObjectNode relation = bundled;
                if (!sources.isEmpty()) {
                    // the subject types move from the union's child to the relation, where typeDefinition reads them
                    ArrayNode directTypes = (ArrayNode) bundled.remove("directly_related_user_types");
                    sources.addFirst(bundled);
                    relation = union(sources);
                    relation.set("directly_related_user_types", directTypes);
                }
                relate(relations, AuthzUtil.permissionName(about.name, permission.getKey()), relation);
            }
        }
        return typeDefinition(AuthzUtil.roleType(home), relations);
    }

    /**
     * A part of a role names some of the resources of the granular types the role holds a permission on: it holds
     * the permission for everyone, on the resources its tuple names. A role's resources are spread over its parts by
     * their hash, so a check reads the one part its resource falls in however many the role names.
     */
    private static ObjectNode rolePartType(List<ResourceType> granular) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        for (String permission : permissionNames(granular)) {
            relate(relations, permission, directUnder(USER + ":*", FOR_DEFINITION));
        }
        return typeDefinition(ROLE_PART, relations);
    }

    // Holds a role's bindings, of whichever type each is attached to, and is held by the members of any of them
    private static ObjectNode roleHoldersType(ArrayNode typeDefinitions) {
        List<String> bindingTypes = new ArrayList<>();
        for (JsonNode type : typeDefinitions) {
            String name = type.get("type").asString();
            if (name.endsWith(AuthzConstants.ROLE_BINDING_TYPE_SUFFIX)) {
                bindingTypes.add(name);
            }
        }
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relate(relations, BINDING, direct(bindingTypes));
        relate(relations, HOLDER, tupleToUserset(BINDING, MEMBER));
        return typeDefinition(ROLE_HOLDERS, relations);
    }

    /**
     * A binding attached to a type is one role granted to some members on one resource of it: a member holds a
     * permission through it when the role bundles that permission. The engine refuses a binding of a role whose
     * home is not one the type may grant.
     */
    private static ObjectNode roleBindingType(String boundOn, Set<String> homes, Set<String> permissions, List<String> memberTypes) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relate(relations, ROLE, direct(homes.stream().map(AuthzUtil::roleType).toList()));
        relate(relations, MEMBER, direct(memberTypes));
        for (String permission : permissions) {
            relate(relations, permission, intersection(List.of(computed(MEMBER), tupleToUserset(ROLE, permission))));
        }
        return typeDefinition(AuthzUtil.roleBindingType(boundOn), relations);
    }

    private static ObjectNode resourceType(ResourceType type, List<ResourceType> carried) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        if (type.parent != null) {
            relate(relations, type.parent, direct(List.of(type.parent)));
        }
        for (Map.Entry<String, Set<String>> membership : new TreeMap<>(type.memberships).entrySet()) {
            relate(relations, membership.getKey(), direct(new ArrayList<>(membership.getValue())));
        }
        for (Map.Entry<String, String> inherited : type.inherited.entrySet()) {
            relate(relations, inherited.getKey(), tupleToUserset(inherited.getValue(), inherited.getKey()));
        }
        relate(relations, ROLE_BINDING, directUnder(AuthzUtil.roleBindingType(type.name), ROLE_HELD));
        for (ResourceType about : carried) {
            for (Map.Entry<String, Set<String>> permission : new TreeMap<>(about.permissions).entrySet()) {
                String name = AuthzUtil.permissionName(about.name, permission.getKey());
                // a role holds every permission one it bundles implies, so a binding of the role is the resource's
                // source of an implied permission
                List<ObjectNode> sources = new ArrayList<>();
                sources.add(tupleToUserset(ROLE_BINDING, name));
                if (type.parent != null) {
                    sources.add(tupleToUserset(type.parent, name));
                }
                // OpenFGA rejects a union of one, so a permission with a single source is that source itself
                relate(relations, name, sources.size() == 1 ? sources.getFirst() : union(sources));
            }
        }
        return typeDefinition(type.name, relations);
    }

    /**
     * A definition's rows within one tenant: the object a tenant's user is checked on, and a grant is made on to
     * reach that tenant's rows of that definition alone. It carries the definition's permissions, each held
     * through a binding on it, through the definition, or through the tenant when the definition is placed in
     * the application, so a definition this store does not hold is reached by no grant on the tenant.
     */
    private static ObjectNode tenantDefinitionType(ResourceType definition) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relate(relations, DEFINITION, direct(List.of(ENTITY_DEFINITION)));
        relate(relations, TENANT, direct(List.of(TENANT)));
        relate(relations, ROLE_BINDING, directUnder(AuthzUtil.roleBindingType(TENANT_DEFINITION), ROLE_HELD));
        relate(relations, PLACED, tupleToUserset(DEFINITION, PLACED));
        for (Map.Entry<String, Set<String>> permission : new TreeMap<>(definition.permissions).entrySet()) {
            String name = AuthzUtil.permissionName(definition.name, permission.getKey());
            List<ObjectNode> sources = new ArrayList<>();
            sources.add(tupleToUserset(ROLE_BINDING, name));
            sources.add(tupleToUserset(DEFINITION, name));
            sources.add(intersection(List.of(tupleToUserset(TENANT, name), computed(PLACED))));
            relate(relations, name, union(sources));
        }
        return typeDefinition(TENANT_DEFINITION, relations);
    }

    // The permissions of a type that imply one of its permissions, each as a relation of the same object
    private static List<ObjectNode> implying(ResourceType type, String permission) {
        List<ObjectNode> ret = new ArrayList<>();
        for (Map.Entry<String, Set<String>> other : new TreeMap<>(type.permissions).entrySet()) {
            if (other.getValue().contains(permission)) {
                ret.add(computed(AuthzUtil.permissionName(type.name, other.getKey())));
            }
        }
        return ret;
    }

    // Gives a type being built a relation. A permission is a relation named <type>_<permission>, so one named as
    // a relation the model defines for the type, such as end_user, would take that relation's place
    private static void relate(Map<String, ObjectNode> relations, String name, ObjectNode userset) {
        if (relations.putIfAbsent(name, userset) != null) {
            throw new IllegalArgumentException("A type of the model would have two relations named '" + name
                                                       + "': a permission's model name, <type>_<permission>, is the name of"
                                                       + " a relation the model defines itself");
        }
    }

    private static ObjectNode typeDefinition(String name, Map<String, ObjectNode> relations) {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ret.put("type", name);
        if (!relations.isEmpty()) {
            ObjectNode relationNodes = ret.putObject("relations");
            ObjectNode metadata = ret.putObject("metadata").putObject("relations");
            for (Map.Entry<String, ObjectNode> relation : relations.entrySet()) {
                ObjectNode userset = relation.getValue();
                ArrayNode directTypes = (ArrayNode) userset.remove("directly_related_user_types");
                relationNodes.set(relation.getKey(), userset);
                metadata.putObject(relation.getKey())
                        .set("directly_related_user_types", directTypes != null ? directTypes : JsonNodeFactory.instance.arrayNode());
            }
        }
        return ret;
    }

    /**
     * A relation assigned by tuples, to subjects of the given types: {@code user}, {@code group#member} for a
     * userset, or {@code user:*} for everyone of the type.
     */
    private static ObjectNode direct(List<String> subjectTypes) {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ret.putObject("this");
        ArrayNode directTypes = ret.putArray("directly_related_user_types");
        for (String subjectType : subjectTypes) {
            ObjectNode reference = directTypes.addObject();
            if (subjectType.endsWith(":*")) {
                reference.put("type", subjectType.substring(0, subjectType.length() - 2));
                reference.putObject("wildcard");
            } else {
                int hash = subjectType.indexOf('#');
                if (hash == -1) {
                    reference.put("type", subjectType);
                } else {
                    reference.put("type", subjectType.substring(0, hash));
                    reference.put("relation", subjectType.substring(hash + 1));
                }
            }
        }
        return ret;
    }

    // A relation assigned by tuples to subjects of one type, each under the condition
    private static ObjectNode directUnder(String subjectType, String condition) {
        ObjectNode ret = direct(List.of(subjectType));
        ((ObjectNode) ret.get("directly_related_user_types").get(0)).put("condition", condition);
        return ret;
    }

    private static ObjectNode computed(String relation) {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ret.putObject("computedUserset").put("object", "").put("relation", relation);
        return ret;
    }

    private static ObjectNode tupleToUserset(String tupleset, String relation) {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ObjectNode node = ret.putObject("tupleToUserset");
        node.putObject("tupleset").put("object", "").put("relation", tupleset);
        node.putObject("computedUserset").put("object", "").put("relation", relation);
        return ret;
    }

    private static ObjectNode union(List<ObjectNode> children) {
        return set("union", children);
    }

    private static ObjectNode intersection(List<ObjectNode> children) {
        return set("intersection", children);
    }

    private static ObjectNode set(String operator, List<ObjectNode> children) {
        ObjectNode ret = JsonNodeFactory.instance.objectNode();
        ArrayNode childNodes = ret.putObject(operator).putArray("child");
        children.forEach(childNodes::add);
        return ret;
    }

}
