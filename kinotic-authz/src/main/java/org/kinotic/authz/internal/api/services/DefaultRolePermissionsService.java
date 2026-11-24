package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import dev.openfga.sdk.api.model.RelationshipCondition;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RolePermissionsService;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;

@Component
@RequiredArgsConstructor
public class DefaultRolePermissionsService implements RolePermissionsService {

    // The engine stores up to 32,768 bytes of condition context in a tuple. The definitions one permission names
    // are spread over parts of a third of that on average, since hashing fills the parts unevenly.
    private static final int PART_CONTEXT_BYTES = 11_000;
    // what the engine counts for a definition named in a tuple, beside the bytes of its id
    private static final int NAMED_DEFINITION_BYTES = 8;

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;

    @Override
    public Future<RolePermissions> findRolePermissions(String store, String roleId) {
        return stores.storeIdOf(store).compose(id -> readRole(id, roleObject(store, roleId))).map(HeldRole::permissions);
    }

    // A role's object, typed by its home. An application's store has a role type for each type it places, so a role
    // homed at a type of the application's own services is held there as a role of the tenant, the level a
    // permission of such a service is checked on
    static String roleObject(String store, String roleId) {
        return AuthzStoreService.places(store, AuthzNameUtil.roleHome(roleId))
                ? AuthzNameUtil.roleObject(roleId)
                : AuthzNameUtil.object(AuthzNameUtil.roleType(AuthzConstants.TENANT_TYPE), roleId);
    }

    // Every relation of a role is a permission it bundles for everything, but the one it holds its parts by; every
    // relation of a part is a permission the role bundles for what the part's tuple names: definitions, or under
    // SERVICE_PERMISSION the permissions of the application's services
    private Future<HeldRole> readRole(String storeId, String role) {
        return fga.read(storeId, new ReadRequestTupleKey()._object(role)).compose(held -> {
            Set<String> everywhere = new TreeSet<>();
            List<String> parts = new ArrayList<>();
            for (Tuple tuple : held) {
                if (DefaultAuthzModelGenerator.PART.equals(tuple.getKey().getRelation())) {
                    parts.add(tuple.getKey().getUser());
                } else {
                    everywhere.add(tuple.getKey().getRelation());
                }
            }
            return OpenFgaService.requestEach(parts, part -> fga.read(storeId, new ReadRequestTupleKey()._object(part))).map(ofParts -> {
                Map<String, Set<String>> named = new TreeMap<>();
                Set<String> ofServices = new TreeSet<>();
                List<TupleKeyWithoutCondition> partTuples = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    partTuples.add(new TupleKeyWithoutCondition().user(parts.get(i)).relation(DefaultAuthzModelGenerator.PART)._object(role));
                    for (Tuple tuple : ofParts.get(i)) {
                        String permission = tuple.getKey().getRelation();
                        partTuples.add(new TupleKeyWithoutCondition().user(tuple.getKey().getUser()).relation(permission)._object(parts.get(i)));
                        Object definitions = ((Map<?, ?>) tuple.getKey().getCondition().getContext()).get(DefaultAuthzModelGenerator.NAMED_DEFINITIONS);
                        for (Object definition : ((Map<?, ?>) definitions).keySet()) {
                            if (DefaultAuthzModelGenerator.SERVICE_PERMISSION.equals(permission)) {
                                ofServices.add(String.valueOf(definition));
                            } else {
                                named.computeIfAbsent(String.valueOf(definition), k -> new TreeSet<>()).add(permission);
                            }
                        }
                    }
                }
                return new HeldRole(new RolePermissions(everywhere, named, ofServices), partTuples);
            });
        });
    }

    @Override
    public Future<Void> ensureRole(String store, String roleId, RolePermissions permissions) {
        return OpenFgaService.converging(() -> reconcileRoles(store, Map.of(roleId, permissions)));
    }

    @Override
    public Future<EngineStore> ensureModelWithRoles(String store, AuthzModel model) {
        Map<String, RolePermissions> bundled = new HashMap<>();
        model.storedRoles().forEach((roleId, permissions) -> bundled.put(roleId, model.bundle(permissions)));
        // the engine validates a tuple against the store's newest version, so the version is written before the
        // roles' tuples that name its relations. A check names the version its store's record holds, which the
        // caller records once the roles are in step
        return stores.ensureModel(store, model)
                     .compose(modelId -> OpenFgaService.converging(() -> reconcileRoles(store, bundled))
                                                       .compose(v -> stores.storeIdOf(store))
                                                       .map(storeId -> new EngineStore(storeId, modelId)));
    }

    private Future<Void> reconcileRoles(String store, Map<String, RolePermissions> roles) {
        List<String> ids = new ArrayList<>(roles.keySet());
        return stores.storeIdOf(store).compose(storeId -> OpenFgaService.requestEach(ids, roleId -> readRole(storeId, roleObject(store, roleId))).compose(held -> {
            List<TupleKey> writes = new ArrayList<>();
            List<TupleKeyWithoutCondition> deletes = new ArrayList<>();
            List<List<TupleKey>> parts = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String role = roleObject(store, ids.get(i));
                RolePermissions desired = roles.get(ids.get(i));
                RolePermissions bundled = held.get(i).permissions();
                for (String permission : bundled.permissions()) {
                    if (!desired.permissions().contains(permission)) {
                        deletes.add(new TupleKeyWithoutCondition().user(AuthzConstants.EVERYONE).relation(permission)._object(role));
                    }
                }
                for (String permission : desired.permissions()) {
                    if (!bundled.permissions().contains(permission)) {
                        writes.add(new TupleKey().user(AuthzConstants.EVERYONE).relation(permission)._object(role));
                    }
                }
                Map<String, Set<String>> named = named(desired);
                // a name falls in a part by the number of parts, so a change to what a role names is every part
                // deleted and written again
                if (!named.equals(named(bundled))) {
                    deletes.addAll(held.get(i).partTuples());
                    parts.addAll(partsOf(role, named));
                }
            }
            // every delete is made before the writes, so a role is never read holding what it held and what it is
            // to hold together; a part is written whole in a request of its own, which keeps each request small
            return fga.write(storeId, List.of(), deletes)
                      .compose(v -> fga.write(storeId, writes, List.of()))
                      .compose(v -> OpenFgaService.requestEach(parts, part -> fga.write(storeId, part, List.of())))
                      .mapEmpty();
        }));
    }

    // What a role holds on the things it names and does not hold for everything: a definition's permissions on the
    // definition, and a permission of the application's services, by its name, under SERVICE_PERMISSION
    private static Map<String, Set<String>> named(RolePermissions permissions) {
        Map<String, Set<String>> ret = new TreeMap<>();
        permissions.definitionPermissions().forEach((definition, held) -> ret.computeIfAbsent(definition, k -> new TreeSet<>()).addAll(held));
        permissions.servicePermissions().forEach(permission -> ret.computeIfAbsent(permission, k -> new TreeSet<>())
                                                                  .add(DefaultAuthzModelGenerator.SERVICE_PERMISSION));
        ret.values().forEach(alone -> alone.removeAll(permissions.permissions()));
        ret.values().removeIf(Set::isEmpty);
        return ret;
    }

    // The tuples of each part of a role: one per permission, naming the definitions that fall in the part and hold
    // it, and the one the role holds the part by. The definitions are spread over the parts by their hash, which a
    // check carries, so a check reads the one part its definition falls in
    private static List<List<TupleKey>> partsOf(String role, Map<String, Set<String>> named) {
        Map<String, Integer> contextBytes = new HashMap<>();
        named.forEach((definition, permissions) -> permissions.forEach(
                permission -> contextBytes.merge(permission, definition.getBytes(StandardCharsets.UTF_8).length + NAMED_DEFINITION_BYTES, Integer::sum)));
        int largest = contextBytes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int count = Math.max(1, (largest + PART_CONTEXT_BYTES - 1) / PART_CONTEXT_BYTES);
        Map<Integer, Map<String, Map<String, Boolean>>> definitionsByPart = new TreeMap<>();
        named.forEach((definition, permissions) -> {
            Map<String, Map<String, Boolean>> ofPart = definitionsByPart.computeIfAbsent((int) (definitionHash(definition) % count), k -> new TreeMap<>());
            permissions.forEach(permission -> ofPart.computeIfAbsent(permission, k -> new TreeMap<>()).put(definition, true));
        });
        List<List<TupleKey>> ret = new ArrayList<>();
        definitionsByPart.forEach((index, ofPart) -> {
            String part = AuthzNameUtil.object(DefaultAuthzModelGenerator.ROLE_PART, AuthzNameUtil.idOf(role) + "/" + index);
            List<TupleKey> tuples = new ArrayList<>();
            ofPart.forEach((permission, definitions) -> tuples.add(
                    new TupleKey().user(AuthzConstants.EVERYONE).relation(permission)._object(part)
                                  .condition(new RelationshipCondition().name(DefaultAuthzModelGenerator.FOR_DEFINITION)
                                                                        .context(Map.of(DefaultAuthzModelGenerator.NAMED_DEFINITIONS, definitions)))));
            tuples.add(new TupleKey().user(part).relation(DefaultAuthzModelGenerator.PART)._object(role)
                                     .condition(new RelationshipCondition().name(DefaultAuthzModelGenerator.IN_PART)
                                                                           .context(Map.of(DefaultAuthzModelGenerator.PART_INDEX, index,
                                                                                           DefaultAuthzModelGenerator.PART_COUNT, count))));
            ret.add(tuples);
        });
        return ret;
    }

    // The hash a definition falls in a part of a role by, which is the same on every node and in every version
    static long definitionHash(String definition) {
        CRC32 ret = new CRC32();
        ret.update(definition.getBytes(StandardCharsets.UTF_8));
        return ret.getValue();
    }
}
