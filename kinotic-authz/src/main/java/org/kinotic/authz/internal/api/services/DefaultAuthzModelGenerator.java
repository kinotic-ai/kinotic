package org.kinotic.authz.internal.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzStoreType;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
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

    static final String USER = AuthzConstants.USER_TYPE;
    static final String GROUP = AuthzConstants.GROUP_TYPE;
    static final String ORGANIZATION = AuthzConstants.ORGANIZATION_TYPE;
    static final String APPLICATION = AuthzConstants.APPLICATION_TYPE;
    static final String TENANT = AuthzConstants.TENANT_TYPE;
    static final String MEMBER = AuthzConstants.MEMBER_RELATION;
    static final String END_USER = AuthzConstants.END_USER_RELATION;
    static final String GRANT = AuthzConstants.GRANT_RELATION;
    static final String ROLE = AuthzConstants.ROLE_RELATION;
    static final String ROLE_BINDING = AuthzConstants.ROLE_BINDING_RELATION;

    private static final String SCHEMA_VERSION = "1.1";
    private static final String ENTITY_DEFINITION = AuthzConstants.ENTITY_DEFINITION_TYPE;
    private static final String TENANT_DEFINITION = AuthzConstants.TENANT_DEFINITION_TYPE;
    private static final String DEFINITION = AuthzConstants.DEFINITION_RELATION;
    private static final String PLACED = AuthzConstants.PLACED_RELATION;

    @Override
    public AuthzModel systemModel(Collection<ServiceDefinition> services) {
        return generate(AuthzStoreType.SYSTEM, services);
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
        return generate(AuthzStoreType.APPLICATION, all);
    }

    private AuthzModel generate(AuthzStoreType storeType, Collection<ServiceDefinition> services) {
        Map<String, ResourceType> types = kernelTypes(storeType);
        declareServiceTypes(services, types);
        declarePermissions(services, types);
        deriveLattice(types);
        validate(types);

        // every ancestor carries each descendant's permissions, so a binding high in the tree reaches down
        Map<String, Set<String>> carried = new TreeMap<>();
        for (ResourceType type : types.values()) {
            for (String ancestor = type.name; ancestor != null; ancestor = types.get(ancestor).parent) {
                carried.computeIfAbsent(ancestor, k -> new TreeSet<>()).add(type.name);
            }
        }
        // in an application's store a tenant is a slice of its application, so it also carries the entity types
        // the application holds for everyone: a check made on a caller's tenant answers for either kind of row,
        // through the tenant's own grants and the application's above it
        if (storeType == AuthzStoreType.APPLICATION) {
            for (ResourceType type : types.values()) {
                if (APPLICATION.equals(type.parent) && !TENANT.equals(type.name)) {
                    carried.get(TENANT).add(type.name);
                }
            }
        }
        Map<String, Set<String>> bindable = bindable(storeType, types, carried);

        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("schema_version", SCHEMA_VERSION);
        ArrayNode typeDefinitions = definition.putArray("type_definitions");
        typeDefinitions.add(typeDefinition(USER, Map.of()));
        typeDefinitions.add(typeDefinition(GROUP, Map.of(MEMBER, direct(List.of(USER, GROUP + "#" + MEMBER)))));
        for (ResourceType type : new TreeMap<>(types).values()) {
            typeDefinitions.add(roleType(type.name, carried.get(type.name), types));
            typeDefinitions.add(roleBindingType(storeType, type.name, bindable.get(type.name), permissionsOf(carried.get(type.name), types)));
            typeDefinitions.add(resourceType(type, types, carried.get(type.name)));
        }
        if (storeType == AuthzStoreType.APPLICATION) {
            ResourceType entityDefinition = types.get(ENTITY_DEFINITION);
            typeDefinitions.add(roleBindingType(storeType, TENANT_DEFINITION, bindable.get(TENANT_DEFINITION),
                                                permissionsOf(Set.of(ENTITY_DEFINITION), types)));
            typeDefinitions.add(tenantDefinitionType(entityDefinition));
        }

        Map<String, Set<String>> catalog = new TreeMap<>();
        for (ResourceType type : types.values()) {
            if (!type.permissions.isEmpty()) {
                catalog.put(type.name, new TreeSet<>(type.permissions.keySet()));
            }
        }
        return new AuthzModel(definition, ModelHash.of(definition), catalog, builtInRoles(storeType, types, carried), bindable);
    }

    /**
     * The home types of the roles a binding on each resource type may grant: the type itself and the types it
     * carries, except those across a trust boundary below it. In the system's store an organization and a tenant
     * are boundaries, so the system grants none of an organization's roles and an organization none of a
     * tenant's; an application's store has none. A definition within a tenant grants the definition's roles.
     */
    private static Map<String, Set<String>> bindable(AuthzStoreType storeType,
                                                     Map<String, ResourceType> types,
                                                     Map<String, Set<String>> carried) {
        Set<String> boundaries = storeType == AuthzStoreType.SYSTEM ? Set.of(ORGANIZATION, TENANT) : Set.of();
        Map<String, Set<String>> ret = new TreeMap<>();
        for (ResourceType type : types.values()) {
            Set<String> homes = new TreeSet<>();
            for (String home : carried.get(type.name)) {
                if (withinBoundary(home, type.name, types, boundaries)) {
                    homes.add(home);
                }
            }
            ret.put(type.name, homes);
        }
        if (storeType == AuthzStoreType.APPLICATION) {
            ret.put(TENANT_DEFINITION, Set.of(ENTITY_DEFINITION));
        }
        return ret;
    }

    // Whether the path up from the home to the type it is bound on crosses no boundary; a home carried by no
    // parent path, as a tenant carries the application's entity types in an application's store, crosses none
    private static boolean withinBoundary(String home, String boundOn, Map<String, ResourceType> types, Set<String> boundaries) {
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
    private static Set<String> permissionsOf(Set<String> typeNames, Map<String, ResourceType> types) {
        Set<String> ret = new TreeSet<>();
        for (String name : typeNames) {
            ResourceType type = types.get(name);
            ret.addAll(modelNames(type, type.permissions.keySet()));
        }
        return ret;
    }

    /**
     * The built-in roles: per type a viewer of its reading permissions, an editor of all but deleting and deciding who may act, an admin
     * of everything on it and inside it, and the roles its services declare; and for a system store the
     * application developer, everything inside an application but the application's own, the system maintainer,
     * the system's own permissions but those deciding who may act, and the reading ones of everything on it, and the system support, the
     * reading permissions of the system and of everything on it. A role that would bundle nothing is not a
     * role.
     */
    private static Map<String, Set<String>> builtInRoles(AuthzStoreType storeType,
                                                         Map<String, ResourceType> types,
                                                         Map<String, Set<String>> carried) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (ResourceType type : types.values()) {
            Set<String> own = modelNames(type, type.permissions.keySet());
            Set<String> editing = new TreeSet<>(own);
            editing.remove(AuthzUtil.permissionName(type.name, AuthzConstants.CAN_DELETE));
            // deciding who may act is an administrator's: managing access would let an editor grant itself any role,
            // and managing machines would let it sign in as one holding more than it does
            editing.removeAll(modelNames(type, type.permissions.keySet().stream().filter(AuthzConstants.AUTHORITY_PERMISSIONS::contains).toList()));
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.VIEWER), reading(type));
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.EDITOR), editing);
            role(ret, AuthzUtil.roleId(type.name, AuthzConstants.ADMIN), inside(type, types, carried, true, false));
        }
        if (storeType == AuthzStoreType.SYSTEM) {
            ResourceType system = types.get(AuthzConstants.SYSTEM_TYPE);
            // the maintainer runs the system but never decides who may act on it, which would let it make itself
            // an administrator
            Set<String> maintaining = modelNames(system, system.permissions.keySet().stream()
                                                                   .filter(permission -> !AuthzConstants.AUTHORITY_PERMISSIONS.contains(permission))
                                                                   .toList());
            maintaining.addAll(inside(system, types, carried, false, true));
            role(ret, AuthzConstants.APPLICATION_DEVELOPER_ROLE, inside(types.get(APPLICATION), types, carried, false, false));
            role(ret, AuthzConstants.SYSTEM_MAINTAINER_ROLE, maintaining);
            role(ret, AuthzConstants.SYSTEM_SUPPORT_ROLE, inside(system, types, carried, true, true));
        }
        for (ResourceType type : types.values()) {
            for (Map.Entry<String, Set<String>> declared : type.declaredRoles.entrySet()) {
                if (ret.containsKey(declared.getKey())) {
                    throw new IllegalArgumentException("Role '" + declared.getKey() + "' is declared by a service of '"
                                                               + type.name + "', but the model already defines it");
                }
                ret.put(declared.getKey(), modelNames(type, declared.getValue()));
            }
        }
        return ret;
    }

    // Every permission of the types inside the given one, with or without the type's own, and either all of
    // them or only the reading ones
    private static Set<String> inside(ResourceType type,
                                      Map<String, ResourceType> types,
                                      Map<String, Set<String>> carried,
                                      boolean own,
                                      boolean readingOnly) {
        Set<String> ret = new TreeSet<>();
        for (String name : carried.getOrDefault(type.name, Set.of())) {
            if (own || !name.equals(type.name)) {
                ResourceType inside = types.get(name);
                ret.addAll(readingOnly ? reading(inside) : modelNames(inside, inside.permissions.keySet()));
            }
        }
        return ret;
    }

    // The model names of a type's reading permissions: can_read and every can_read_<something>
    private static Set<String> reading(ResourceType type) {
        Set<String> ret = new TreeSet<>();
        for (String permission : type.permissions.keySet()) {
            if (AuthzUtil.isReading(permission)) {
                ret.add(AuthzUtil.permissionName(type.name, permission));
            }
        }
        return ret;
    }

    private static Set<String> modelNames(ResourceType type, Collection<String> permissions) {
        Set<String> ret = new TreeSet<>();
        for (String permission : permissions) {
            ret.add(AuthzUtil.permissionName(type.name, permission));
        }
        return ret;
    }

    private static void role(Map<String, Set<String>> roles, String id, Set<String> permissions) {
        if (!permissions.isEmpty()) {
            roles.put(id, permissions);
        }
    }

    /**
     * The types every store of this type has, with the membership relations the identity model writes.
     */
    private static Map<String, ResourceType> kernelTypes(AuthzStoreType storeType) {
        Map<String, ResourceType> ret = new LinkedHashMap<>();
        if (storeType == AuthzStoreType.SYSTEM) {
            ret.put(AuthzConstants.SYSTEM_TYPE, new ResourceType(AuthzConstants.SYSTEM_TYPE, null));
            ResourceType organization = new ResourceType(ORGANIZATION, AuthzConstants.SYSTEM_TYPE);
            organization.memberships.put(MEMBER, Set.of(USER));
            ret.put(ORGANIZATION, organization);
            ResourceType application = new ResourceType(APPLICATION, ORGANIZATION);
            application.memberships.put(END_USER, Set.of(USER));
            ret.put(APPLICATION, application);
        } else {
            ResourceType application = new ResourceType(APPLICATION, null);
            application.memberships.put(END_USER, Set.of(USER));
            // held by everyone through the one wildcard tuple the store is provisioned with, so a definition
            // under the application is told from one of another application by reading it through the definition
            application.memberships.put(PLACED, Set.of(AuthzConstants.EVERYONE));
            ret.put(APPLICATION, application);
            // the definition the request names, whose permissions the entities repository declares; placed is held
            // by everyone for a definition under the application, by no one for one this store does not hold
            ResourceType definition = new ResourceType(ENTITY_DEFINITION, APPLICATION);
            definition.inherited.put(PLACED, APPLICATION);
            ret.put(ENTITY_DEFINITION, definition);
        }
        ResourceType tenant = new ResourceType(TENANT, APPLICATION);
        tenant.memberships.put(MEMBER, Set.of(USER));
        ret.put(TENANT, tenant);
        return ret;
    }

    private static void declareServiceTypes(Collection<ServiceDefinition> services, Map<String, ResourceType> types) {
        for (ServiceDefinition service : services) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            if (resource != null) {
                ResourceType type = types.get(resource.getResourceType());
                if (type == null) {
                    type = new ResourceType(resource.getResourceType(), resource.getParent());
                    types.put(type.name, type);
                } else if (resource.getParent() != null && !resource.getParent().equals(type.parent)) {
                    // the kernel's parent, or another service's, is the one in the tree; a second parent would fork it
                    throw new IllegalArgumentException("Service " + service.getQualifiedName() + " declares the parent of '"
                                                               + type.name + "' as '" + resource.getParent()
                                                               + "', but it is '" + type.parent + "'");
                }
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

    // the derived verbs form a ladder: deleting implies editing, editing implies reading, wherever both exist
    private static void deriveLattice(Map<String, ResourceType> types) {
        for (ResourceType type : types.values()) {
            implyIfBoth(type, AuthzConstants.CAN_DELETE, AuthzConstants.CAN_EDIT);
            implyIfBoth(type, AuthzConstants.CAN_EDIT, AuthzConstants.CAN_READ);
        }
    }

    private static void implyIfBoth(ResourceType type, String stronger, String weaker) {
        if (type.permissions.containsKey(stronger) && type.permissions.containsKey(weaker)) {
            type.permissions.get(stronger).add(weaker);
        }
    }

    private static void validate(Map<String, ResourceType> types) {
        for (ResourceType type : types.values()) {
            // the model names a type's roles and bindings by suffixing it, so a type named like one would collide
            if (type.name.endsWith(AuthzConstants.ROLE_TYPE_SUFFIX) || type.name.endsWith(AuthzConstants.ROLE_BINDING_TYPE_SUFFIX)) {
                throw new IllegalArgumentException("Resource type '" + type.name + "' ends with '" + AuthzConstants.ROLE_TYPE_SUFFIX
                                                           + "' or '" + AuthzConstants.ROLE_BINDING_TYPE_SUFFIX
                                                           + "', which name the model's roles and bindings");
            }
            if (type.parent != null && !types.containsKey(type.parent)) {
                throw new IllegalArgumentException("Resource type '" + type.name + "' declares the parent '" + type.parent
                                                           + "', which this model does not have");
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
                for (String implied : permission.getValue()) {
                    if (!type.permissions.containsKey(implied)) {
                        throw new IllegalArgumentException("Permission '" + name + "' implies '" + implied
                                                                   + "', which '" + type.name + "' does not have");
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
     * every permission a permission it bundles implies. The engine refuses a role bundling a permission from
     * above its home.
     */
    private static ObjectNode roleType(String home, Set<String> carriedTypes, Map<String, ResourceType> types) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relations.put(GRANT, direct(List.of(USER + ":*")));
        for (String carried : carriedTypes) {
            ResourceType about = types.get(carried);
            for (Map.Entry<String, Set<String>> permission : new TreeMap<>(about.permissions).entrySet()) {
                ObjectNode bundled = direct(List.of(USER + ":*"));
                List<ObjectNode> sources = implying(about, permission.getKey());
                ObjectNode relation = bundled;
                if (!sources.isEmpty()) {
                    // the subject types move from the union's child to the relation, where typeDefinition reads them
                    ArrayNode directTypes = (ArrayNode) bundled.remove("directly_related_user_types");
                    sources.addFirst(bundled);
                    relation = union(sources);
                    relation.set("directly_related_user_types", directTypes);
                }
                relations.put(AuthzUtil.permissionName(about.name, permission.getKey()), relation);
            }
        }
        return typeDefinition(AuthzUtil.roleType(home), relations);
    }

    /**
     * A binding attached to a type is one role granted to some members on one resource of it: a member holds a
     * permission through it when the role bundles that permission. The engine refuses a binding of a role whose
     * home is not one the type may grant.
     */
    private static ObjectNode roleBindingType(AuthzStoreType storeType, String boundOn, Set<String> homes, Set<String> permissions) {
        List<String> memberTypes = new ArrayList<>(List.of(USER, GROUP + "#" + MEMBER));
        if (storeType == AuthzStoreType.SYSTEM) {
            memberTypes.add(ORGANIZATION + "#" + MEMBER);
        }
        memberTypes.add(APPLICATION + "#" + END_USER);
        memberTypes.add(TENANT + "#" + MEMBER);
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relations.put(ROLE, direct(homes.stream().map(AuthzUtil::roleType).toList()));
        relations.put(MEMBER, direct(memberTypes));
        for (String permission : permissions) {
            relations.put(permission, intersection(List.of(computed(MEMBER), tupleToUserset(ROLE, permission))));
        }
        return typeDefinition(AuthzUtil.roleBindingType(boundOn), relations);
    }

    private static ObjectNode resourceType(ResourceType type, Map<String, ResourceType> types, Set<String> carriedTypes) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        if (type.parent != null) {
            relations.put(type.parent, direct(List.of(type.parent)));
        }
        for (Map.Entry<String, Set<String>> membership : new TreeMap<>(type.memberships).entrySet()) {
            relations.put(membership.getKey(), direct(new ArrayList<>(membership.getValue())));
        }
        for (Map.Entry<String, String> inherited : type.inherited.entrySet()) {
            relations.put(inherited.getKey(), tupleToUserset(inherited.getValue(), inherited.getKey()));
        }
        relations.put(ROLE_BINDING, direct(List.of(AuthzUtil.roleBindingType(type.name))));
        for (String carried : carriedTypes) {
            ResourceType about = types.get(carried);
            for (Map.Entry<String, Set<String>> permission : new TreeMap<>(about.permissions).entrySet()) {
                String name = AuthzUtil.permissionName(about.name, permission.getKey());
                List<ObjectNode> sources = new ArrayList<>();
                sources.add(tupleToUserset(ROLE_BINDING, name));
                sources.addAll(implying(about, permission.getKey()));
                if (type.parent != null) {
                    sources.add(tupleToUserset(type.parent, name));
                }
                // OpenFGA rejects a union of one, so a permission with a single source is that source itself
                relations.put(name, sources.size() == 1 ? sources.getFirst() : union(sources));
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
        relations.put(DEFINITION, direct(List.of(ENTITY_DEFINITION)));
        relations.put(TENANT, direct(List.of(TENANT)));
        relations.put(ROLE_BINDING, direct(List.of(AuthzUtil.roleBindingType(TENANT_DEFINITION))));
        relations.put(PLACED, tupleToUserset(DEFINITION, PLACED));
        for (Map.Entry<String, Set<String>> permission : new TreeMap<>(definition.permissions).entrySet()) {
            String name = AuthzUtil.permissionName(definition.name, permission.getKey());
            List<ObjectNode> sources = new ArrayList<>();
            sources.add(tupleToUserset(ROLE_BINDING, name));
            sources.addAll(implying(definition, permission.getKey()));
            sources.add(tupleToUserset(DEFINITION, name));
            sources.add(intersection(List.of(tupleToUserset(TENANT, name), computed(PLACED))));
            relations.put(name, union(sources));
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
