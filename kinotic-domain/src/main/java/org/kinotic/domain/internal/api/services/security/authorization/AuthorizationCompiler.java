package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.internal.model.security.AuthorizationTuple;
import java.util.*;

/** Compiles UI-managed roles to a shallow, deterministic permission graph. */
public class AuthorizationCompiler {
    public static String user(String id) { return "identity:" + AuthorizationScope.encode(id); }
    public static String group(AuthorizationScope scope, String id) { return "group:" + AuthorizationScope.encode(scope.key(), id); }
    public static String resource(AuthorizationScope scope, String type, String id, String permission) {
        return type + ":" + AuthorizationScope.encode(scope.key(), id, permission);
    }
    public static String grant(AuthorizationScope scope, String type, String id, String permission) {
        return "authorization_scope:" + AuthorizationScope.encode(scope.key(), type, id, permission);
    }
    public static boolean matches(String pattern, String permission) {
        return pattern.equals("*") || pattern.equals(permission)
                || (pattern.endsWith(".*") && permission.startsWith(pattern.substring(0, pattern.length() - 1)));
    }
    public static void validatePermission(AuthorizationPermission permission) {
        if (permission.getPermission() == null || !permission.getPermission().matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")) throw new IllegalArgumentException("Invalid permission name");
        String type = permission.getResourceType();
        if (type == null || !type.matches("[A-Za-z][A-Za-z0-9_]{0,62}") || List.of("identity", "group", "authorization_scope").contains(type)) throw new IllegalArgumentException("Invalid resource type");
    }
    public static List<AuthorizationTuple> project(AuthorizationPolicy policy, List<AuthorizationPermission> catalog) {
        Set<AuthorizationTuple> tuples = new LinkedHashSet<>();
        Map<String, List<AuthorizationPermission>> roles = new HashMap<>();
        for (var permission : catalog) validatePermission(permission);
        for (var role : policy.getRoles()) roles.put(role.getId(), catalog.stream()
                .filter(permission -> role.getPermissions().stream().anyMatch(pattern -> matches(pattern, permission.getPermission()))).toList());
        for (var group : policy.getGroups()) for (String member : group.getMemberIds())
            addTuple(tuples, new AuthorizationTuple(user(member), "member", group(policy.getScope(), group.getId())));
        for (var permission : catalog) {
            validatePermission(permission);
            for (String administrator : policy.getAdministrators()) addTuple(tuples, new AuthorizationTuple(user(administrator), "allow",
                    grant(policy.getScope(), permission.getResourceType(), "*", permission.getPermission())));
        }
        for (var assignment : policy.getAssignments()) {
            String subject = assignment.getSubjectKind() == AuthorizationSubjectKind.IDENTITY ? user(assignment.getSubjectId())
                    : group(policy.getScope(), assignment.getSubjectId()) + "#member";
            String target = assignment.getSelector() == AuthorizationSelector.ALL ? "*" : assignment.getResourceId();
            for (var permission : roles.get(assignment.getRoleId())) {
                if (!assignment.getResourceType().equals(permission.getResourceType())) continue;
                addTuple(tuples, new AuthorizationTuple(subject, assignment.getEffect() == AuthorizationEffect.ALLOW ? "allow" : "deny",
                        grant(policy.getScope(), permission.getResourceType(), target, permission.getPermission())));
            }
        }
        return List.copyOf(tuples);
    }
    private static void addTuple(Set<AuthorizationTuple> tuples, AuthorizationTuple tuple) {
        if (tuple.user().length() > 512 || tuple.object().length() > 512) throw new IllegalArgumentException("Authorization identifier exceeds OpenFGA limits");
        tuples.add(tuple);
        if (tuples.size() > 100_000) throw new IllegalArgumentException("Authorization projection exceeds 100000 tuples");
    }
    public static List<AuthorizationTuple> parents(AuthorizationScope scope, String type, String id, String permission) {
        String object = resource(scope, type, id, permission);
        var scopes = scope.getKind() == AuthorizationScopeKind.APPLICATION_TENANT ? List.of(scope, scope.applicationScope()) : List.of(scope);
        var tuples = new LinkedHashSet<AuthorizationTuple>();
        for (var parent : scopes) for (String target : new LinkedHashSet<>(List.of("*", id)))
            addTuple(tuples, new AuthorizationTuple(grant(parent, type, target, permission), "parent", object));
        return List.copyOf(tuples);
    }
    public static JsonObject model(Collection<String> types) {
        if (new HashSet<>(types).size() > 97) throw new IllegalArgumentException("OpenFGA supports at most 97 application resource types with this model");
        var definitions = new JsonArray().add(new JsonObject().put("type", "identity"));
        definitions.add(directType("group", List.of("member"), false));
        definitions.add(directType("authorization_scope", List.of("allow", "deny"), true));
        for (String type : new TreeSet<>(types)) {
            validatePermission(new AuthorizationPermission().setPermission("access").setResourceType(type));
            var relations = new JsonObject().put("parent", new JsonObject().put("this", new JsonObject()))
                    .put("access", new JsonObject().put("difference", new JsonObject().put("base", arrow("allow")).put("subtract", arrow("deny"))));
            definitions.add(new JsonObject().put("type", type).put("relations", relations).put("metadata", new JsonObject().put("relations",
                    new JsonObject().put("parent", new JsonObject().put("directly_related_user_types", new JsonArray().add(new JsonObject().put("type", "authorization_scope")))))));
        }
        return new JsonObject().put("schema_version", "1.1").put("type_definitions", definitions);
    }
    private static JsonObject directType(String type, List<String> names, boolean groups) {
        var relations = new JsonObject();var metadata = new JsonObject();
        for (String name : names) {
            relations.put(name, new JsonObject().put("this", new JsonObject()));
            var refs = new JsonArray().add(new JsonObject().put("type", "identity"));
            if (groups) refs.add(new JsonObject().put("type", "group").put("relation", "member"));
            metadata.put(name, new JsonObject().put("directly_related_user_types", refs));
        }
        return new JsonObject().put("type", type).put("relations", relations).put("metadata", new JsonObject().put("relations", metadata));
    }
    private static JsonObject arrow(String relation) {
        return new JsonObject().put("tupleToUserset", new JsonObject().put("tupleset", new JsonObject().put("relation", "parent"))
                .put("computedUserset", new JsonObject().put("relation", relation)));
    }
}
