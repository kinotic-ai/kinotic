package org.kinotic.authz.internal.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzStoreKind;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.authz.api.model.EntityScope;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
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

    static final String USER = AuthzUtil.USER_TYPE;
    static final String GROUP = AuthzUtil.GROUP_TYPE;
    static final String ROLE = AuthzUtil.ROLE_TYPE;
    static final String ROLE_BINDING = AuthzUtil.ROLE_BINDING_TYPE;
    static final String ORGANIZATION = AuthzUtil.ORGANIZATION_TYPE;
    static final String APPLICATION = AuthzUtil.APPLICATION_TYPE;
    static final String TENANT = AuthzUtil.TENANT_TYPE;
    static final String MEMBER = AuthzUtil.MEMBER_RELATION;
    static final String END_USER = AuthzUtil.END_USER_RELATION;
    static final String GRANT = AuthzUtil.GRANT_RELATION;

    private static final String SCHEMA_VERSION = "1.1";
    private static final String CAN_READ = "can_read";
    private static final String CAN_CREATE = "can_create";
    private static final String CAN_SEARCH = "can_search";

    @Override
    public AuthzModel platformModel(Collection<ServiceDefinition> services) {
        return generate(AuthzStoreKind.PLATFORM, services, List.of());
    }

    @Override
    public AuthzModel applicationModel(Collection<ServiceDefinition> platformServices,
                                       Collection<ServiceDefinition> services,
                                       Collection<EntityResource> entities) {
        List<ServiceDefinition> all = new ArrayList<>();
        for (ServiceDefinition service : platformServices) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            if (resource != null && TENANT.equals(resource.getResourceType())) {
                all.add(service);
            }
        }
        all.addAll(services);
        return generate(AuthzStoreKind.APPLICATION, all, entities);
    }

    private AuthzModel generate(AuthzStoreKind kind,
                                Collection<ServiceDefinition> services,
                                Collection<EntityResource> entities) {
        Map<String, ResourceType> types = kernelTypes(kind);
        declareServiceTypes(services, types);
        declareEntityTypes(entities, types);
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
        if (kind == AuthzStoreKind.APPLICATION) {
            for (ResourceType type : types.values()) {
                if (APPLICATION.equals(type.parent) && !TENANT.equals(type.name)) {
                    carried.get(TENANT).add(type.name);
                }
            }
        }
        Set<String> allPermissions = new TreeSet<>();
        for (ResourceType type : types.values()) {
            for (String permission : type.permissions.keySet()) {
                allPermissions.add(AuthzUtil.permissionName(type.name, permission));
            }
        }

        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("schema_version", SCHEMA_VERSION);
        ArrayNode typeDefinitions = definition.putArray("type_definitions");
        typeDefinitions.add(typeDefinition(USER, Map.of()));
        typeDefinitions.add(typeDefinition(GROUP, Map.of(MEMBER, direct(List.of(USER, GROUP + "#" + MEMBER)))));
        typeDefinitions.add(roleType(allPermissions));
        typeDefinitions.add(roleBindingType(kind, allPermissions));
        for (ResourceType type : new TreeMap<>(types).values()) {
            typeDefinitions.add(resourceType(type, types, carried.get(type.name)));
        }

        Map<String, Set<String>> catalog = new TreeMap<>();
        for (ResourceType type : types.values()) {
            if (!type.permissions.isEmpty()) {
                catalog.put(type.name, new TreeSet<>(type.permissions.keySet()));
            }
        }
        return new AuthzModel(definition, ModelHash.of(definition), catalog, builtInRoles(kind, types, carried));
    }

    /**
     * The built-in roles: per type a viewer of its reading permissions, an editor of all but deleting, an admin
     * of everything on it and inside it, and the roles its services declare; and for a platform store the
     * application developer, everything inside an application but the application's own, the platform operator,
     * the platform's own permissions and the reading ones of everything on it, and the platform support, the
     * reading permissions of the platform and of everything on it. A role that would bundle nothing is not a
     * role.
     */
    private static Map<String, Set<String>> builtInRoles(AuthzStoreKind kind,
                                                         Map<String, ResourceType> types,
                                                         Map<String, Set<String>> carried) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (ResourceType type : types.values()) {
            Set<String> own = modelNames(type, type.permissions.keySet());
            Set<String> editing = new TreeSet<>(own);
            editing.remove(AuthzUtil.permissionName(type.name, AuthzUtil.CAN_DELETE));
            role(ret, AuthzUtil.roleId(type.name, AuthzUtil.VIEWER), viewing(type));
            role(ret, AuthzUtil.roleId(type.name, AuthzUtil.EDITOR), editing);
            role(ret, AuthzUtil.roleId(type.name, AuthzUtil.ADMIN), inside(type, types, carried, true, false));
        }
        if (kind == AuthzStoreKind.PLATFORM) {
            ResourceType platform = types.get(AuthzUtil.PLATFORM_TYPE);
            Set<String> operating = modelNames(platform, platform.permissions.keySet());
            operating.addAll(inside(platform, types, carried, false, true));
            role(ret, AuthzUtil.APPLICATION_DEVELOPER_ROLE, inside(types.get(APPLICATION), types, carried, false, false));
            role(ret, AuthzUtil.PLATFORM_OPERATOR_ROLE, operating);
            role(ret, AuthzUtil.PLATFORM_SUPPORT_ROLE, inside(platform, types, carried, true, true));
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
                                      boolean viewingOnly) {
        Set<String> ret = new TreeSet<>();
        for (String name : carried.getOrDefault(type.name, Set.of())) {
            if (own || !name.equals(type.name)) {
                ResourceType inside = types.get(name);
                ret.addAll(viewingOnly ? viewing(inside) : modelNames(inside, inside.permissions.keySet()));
            }
        }
        return ret;
    }

    // The model names of a type's reading permissions: the ones that only read, among them every can_view_<something>
    private static Set<String> viewing(ResourceType type) {
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
     * The types every store of the kind has, with the membership relations the identity model writes.
     */
    private static Map<String, ResourceType> kernelTypes(AuthzStoreKind kind) {
        Map<String, ResourceType> ret = new LinkedHashMap<>();
        if (kind == AuthzStoreKind.PLATFORM) {
            ret.put(AuthzUtil.PLATFORM_TYPE, new ResourceType(AuthzUtil.PLATFORM_TYPE, null));
            ResourceType organization = new ResourceType(ORGANIZATION, AuthzUtil.PLATFORM_TYPE);
            organization.memberships.put(MEMBER, Set.of(USER));
            ret.put(ORGANIZATION, organization);
            ResourceType application = new ResourceType(APPLICATION, ORGANIZATION);
            application.memberships.put(END_USER, Set.of(USER));
            ret.put(APPLICATION, application);
        } else {
            ResourceType application = new ResourceType(APPLICATION, null);
            application.memberships.put(END_USER, Set.of(USER));
            ret.put(APPLICATION, application);
        }
        ResourceType tenant = new ResourceType(TENANT, APPLICATION);
        tenant.memberships.put(MEMBER, Set.of(USER));
        ret.put(TENANT, tenant);
        return ret;
    }

    private static void declareServiceTypes(Collection<ServiceDefinition> services, Map<String, ResourceType> types) {
        for (ServiceDefinition service : services) {
            AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
            // a service whose type each request names acts on the entity types, declared with the entities
            if (resource != null && !AuthzUtil.isTemplate(resource.getResourceType())) {
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

    private static void declareEntityTypes(Collection<EntityResource> entities, Map<String, ResourceType> types) {
        for (EntityResource entity : entities) {
            if (!AuthzUtil.isIdentifier(entity.typeName())) {
                throw new IllegalArgumentException("Entity definition '" + entity.typeName()
                                                           + "' is not a lowercase identifier and cannot be a resource type");
            }
            if (types.containsKey(entity.typeName())) {
                throw new IllegalArgumentException("Entity definition '" + entity.typeName()
                                                           + "' collides with a resource type of the same name");
            }
            ResourceType type = new ResourceType(entity.typeName(),
                                                 entity.scope() == EntityScope.TENANT ? TENANT : APPLICATION);
            type.addPermission(CAN_SEARCH, List.of());
            type.addPermission(CAN_READ, List.of(CAN_SEARCH));
            type.addPermission(CAN_CREATE, List.of());
            type.addPermission(AuthzUtil.CAN_EDIT, List.of(CAN_READ));
            type.addPermission(AuthzUtil.CAN_DELETE, List.of(AuthzUtil.CAN_EDIT));
            types.put(type.name, type);
        }
    }

    private static void declarePermissions(Collection<ServiceDefinition> services, Map<String, ResourceType> types) {
        for (ServiceDefinition service : services) {
            for (FunctionDefinition function : service.getFunctions()) {
                AuthzCheckC3Decorator check = function.findDecorator(AuthzCheckC3Decorator.class);
                // a permission about a type the request names is the entity type's own, declared with the entity
                if (check != null && !AuthzUtil.isTemplate(check.getPermissionResource())) {
                    ResourceType type = types.get(check.getPermissionResource());
                    if (type == null) {
                        throw new IllegalArgumentException("Function " + function.getName() + " of " + service.getQualifiedName()
                                                                   + " requires a permission of '" + check.getPermissionResource()
                                                                   + "', which no service or entity definition declares");
                    }
                    type.addPermission(check.getPermission(), check.getImplies());
                }
            }
        }
    }

    // the derived verbs form a ladder: deleting implies editing, editing implies viewing, wherever both exist
    private static void deriveLattice(Map<String, ResourceType> types) {
        for (ResourceType type : types.values()) {
            implyIfBoth(type, AuthzUtil.CAN_DELETE, AuthzUtil.CAN_EDIT);
            implyIfBoth(type, AuthzUtil.CAN_EDIT, AuthzUtil.CAN_VIEW);
        }
    }

    private static void implyIfBoth(ResourceType type, String stronger, String weaker) {
        if (type.permissions.containsKey(stronger) && type.permissions.containsKey(weaker)) {
            type.permissions.get(stronger).add(weaker);
        }
    }

    private static void validate(Map<String, ResourceType> types) {
        for (ResourceType type : types.values()) {
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
                if (name.length() > AuthzUtil.MAX_RELATION_NAME_LENGTH) {
                    throw new IllegalArgumentException("Permission '" + name + "' is longer than the "
                                                               + AuthzUtil.MAX_RELATION_NAME_LENGTH + " characters a relation name may have");
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
     * A role is a bundle of permissions: for each permission the model has, the role holds it for everyone or for
     * nobody, which the binding then narrows to its members.
     */
    private static ObjectNode roleType(Set<String> allPermissions) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relations.put(GRANT, direct(List.of(USER + ":*")));
        for (String permission : allPermissions) {
            relations.put(permission, direct(List.of(USER + ":*")));
        }
        return typeDefinition(ROLE, relations);
    }

    /**
     * A binding is one role granted to some members on one resource: a member holds a permission through it
     * when the role bundles that permission.
     */
    private static ObjectNode roleBindingType(AuthzStoreKind kind, Set<String> allPermissions) {
        List<String> memberTypes = new ArrayList<>(List.of(USER, GROUP + "#" + MEMBER));
        if (kind == AuthzStoreKind.PLATFORM) {
            memberTypes.add(ORGANIZATION + "#" + MEMBER);
        }
        memberTypes.add(APPLICATION + "#" + END_USER);
        memberTypes.add(TENANT + "#" + MEMBER);
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        relations.put(ROLE, direct(List.of(ROLE)));
        relations.put(MEMBER, direct(memberTypes));
        for (String permission : allPermissions) {
            relations.put(permission, intersection(List.of(computed(MEMBER), tupleToUserset(ROLE, permission))));
        }
        return typeDefinition(ROLE_BINDING, relations);
    }

    private static ObjectNode resourceType(ResourceType type, Map<String, ResourceType> types, Set<String> carriedTypes) {
        Map<String, ObjectNode> relations = new LinkedHashMap<>();
        if (type.parent != null) {
            relations.put(type.parent, direct(List.of(type.parent)));
        }
        for (Map.Entry<String, Set<String>> membership : new TreeMap<>(type.memberships).entrySet()) {
            relations.put(membership.getKey(), direct(new ArrayList<>(membership.getValue())));
        }
        relations.put(ROLE_BINDING, direct(List.of(ROLE_BINDING)));
        for (String carried : carriedTypes) {
            ResourceType about = types.get(carried);
            for (Map.Entry<String, Set<String>> permission : new TreeMap<>(about.permissions).entrySet()) {
                String name = AuthzUtil.permissionName(about.name, permission.getKey());
                List<ObjectNode> sources = new ArrayList<>();
                sources.add(tupleToUserset(ROLE_BINDING, name));
                for (Map.Entry<String, Set<String>> other : new TreeMap<>(about.permissions).entrySet()) {
                    if (other.getValue().contains(permission.getKey())) {
                        sources.add(computed(AuthzUtil.permissionName(about.name, other.getKey())));
                    }
                }
                if (type.parent != null) {
                    sources.add(tupleToUserset(type.parent, name));
                }
                // OpenFGA rejects a union of one, so a permission with a single source is that source itself
                relations.put(name, sources.size() == 1 ? sources.getFirst() : union(sources));
            }
        }
        return typeDefinition(type.name, relations);
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
