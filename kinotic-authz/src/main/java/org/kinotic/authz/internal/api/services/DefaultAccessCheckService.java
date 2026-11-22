package org.kinotic.authz.internal.api.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.ContextualTupleKeys;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.errors.ApiException;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AccessCheckService;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAccessCheckService implements AccessCheckService {

    private static final long HELD_ROLES_CAPACITY = 100_000;
    private static final Duration HELD_ROLES_RETENTION = Duration.ofMinutes(5);

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;
    // The ids of the roles a user holds on anything in a store, as last listed, by heldRolesKey. A check names them
    // so the engine reads the bindings of those roles alone. A role missing from a kept list is found by the
    // listing a refusal is followed by, and one the user no longer holds names a binding the user is no member of
    private final Cache<String, Set<String>> heldRoles = Caffeine.newBuilder()
                                                                 .maximumSize(HELD_ROLES_CAPACITY)
                                                                 .expireAfterWrite(HELD_ROLES_RETENTION)
                                                                 .build();

    // What a user holds when it is one of those a grant to a group, a tenant or an application stands for
    private static RelationshipTuple oneOf(String user, Subject subject) {
        return new RelationshipTuple(user, subject.type().getRelation(), AuthzNameUtil.object(subject.type().getObjectType(), subject.id()));
    }

    @Override
    public Future<AccessExplanation> explain(String store,
                                             AuthzModel model,
                                             RelationshipTuple holds,
                                             List<RelationshipTuple> context,
                                             List<Grant> grants) {
        // a permission of an application's own services is asked as the relation standing for them all, by its name
        boolean ofService = model.servicePermissions().containsKey(holds.relation());
        RelationshipTuple asked = ofService
                ? new RelationshipTuple(holds.user(), DefaultAuthzModelGenerator.SERVICE_PERMISSION, holds.object())
                : holds;
        String named = ofService ? holds.relation() : definitionOf(holds.object());
        List<RelationshipTuple> held = new ArrayList<>(context);
        held.addAll(holdingByDeclaration(store, model.rolesHolding(holds.relation())));
        // a role has a relation only for the permissions of its home and of the types inside it, and the engine
        // refuses a check for any other, so a grant of a role that cannot hold the permission is left out unasked
        List<Grant> candidates = grants.stream()
                                       .filter(grant -> model.hasRelation(AuthzNameUtil.typeOf(DefaultRolePermissionsService.roleObject(store, grant.roleId())), asked.relation()))
                                       .toList();
        return stores.modelId(store).compose(modelId -> {
            // an administrator asks after changing access, so the answer must not predate the change
            Future<Boolean> allowed = check(store, modelId, asked, Consistency.HIGHER_CONSISTENCY, held, named);
            return OpenFgaService.requestEach(candidates, grant -> explains(store, modelId, grant, asked, held, named))
                    .compose(explains -> allowed.map(holding -> {
                        List<Grant> through = new ArrayList<>();
                        for (int i = 0; i < candidates.size(); i++) {
                            if (explains.get(i)) {
                                through.add(candidates.get(i));
                            }
                        }
                        return new AccessExplanation(holding, through);
                    }));
        });
    }

    // Whether the grant gives the permission asked about to the user asked about: its role bundles the permission
    // or one implying it, and it was made to the user, or to a group or a tenant the user is a member of, or to
    // the application the user is an end user of
    private Future<Boolean> explains(String store,
                                     String modelId,
                                     Grant grant,
                                     RelationshipTuple holds,
                                     List<RelationshipTuple> context,
                                     String named) {
        // the role's relation also holds every permission a bundled one implies, and one bundled for what the
        // question names, which only a check evaluates
        RelationshipTuple bundles = new RelationshipTuple(AuthzConstants.EVERYONE, holds.relation(), DefaultRolePermissionsService.roleObject(store, grant.roleId()));
        Future<Boolean> bundled = check(store, modelId, bundles, Consistency.HIGHER_CONSISTENCY, context, named);
        Future<Boolean> member;
        if (grant.subject().equals(AuthzNameUtil.subjectOf(holds.user()))) {
            member = Future.succeededFuture(true);
        } else if (grant.subject().type() != SubjectType.USER && AuthzConstants.USER_TYPE.equals(AuthzNameUtil.typeOf(holds.user()))) {
            // an admin asks after changing a group or moving a user, so the answer must not predate the change
            member = check(store, modelId, oneOf(holds.user(), grant.subject()), Consistency.HIGHER_CONSISTENCY, List.of(), "");
        } else {
            member = Future.succeededFuture(false);
        }
        return Future.all(bundled, member).map(both -> both.<Boolean>resultAt(0) && both.<Boolean>resultAt(1));
    }

    @Override
    public Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context) {
        return stores.modelId(store).compose(modelId -> check(store, modelId, relationship, consistency, context, definitionOf(relationship.object())));
    }

    @Override
    public Future<Boolean> checkServicePermission(String store,
                                                  String user,
                                                  String permission,
                                                  String object,
                                                  Set<String> roles,
                                                  Consistency consistency) {
        RelationshipTuple relationship = new RelationshipTuple(user, DefaultAuthzModelGenerator.SERVICE_PERMISSION, object);
        List<RelationshipTuple> held = holdingByDeclaration(store, roles);
        return stores.modelId(store).compose(modelId -> check(store, modelId, relationship, consistency, held, permission));
    }

    // A built-in role of a type of an application's own services holds its permissions by the contracts declaring
    // them and by no stored tuple, so a check of one such permission takes each role holding it as holding every
    // permission of the services, for this check alone
    private static List<RelationshipTuple> holdingByDeclaration(String store, Set<String> roles) {
        List<RelationshipTuple> ret = new ArrayList<>();
        for (String roleId : roles) {
            ret.add(new RelationshipTuple(AuthzConstants.EVERYONE, DefaultAuthzModelGenerator.SERVICE_PERMISSION, DefaultRolePermissionsService.roleObject(store, roleId)));
        }
        return ret;
    }

    // The definition a question about an object is asked for: the object when it is one, the one of a definition
    // within a tenant, and the empty one, which no role names, for any other object
    private static String definitionOf(String about) {
        String type = AuthzNameUtil.typeOf(about);
        String ret = "";
        if (AuthzConstants.ENTITY_DEFINITION_TYPE.equals(type)) {
            ret = AuthzNameUtil.idOf(about);
        } else if (AuthzConstants.TENANT_DEFINITION_TYPE.equals(type)) {
            ret = AuthzNameUtil.definitionOf(AuthzNameUtil.idOf(about));
        }
        return ret;
    }

    /**
     * A check's request, for whichever version answers it.
     *
     * @param named what the question is asked for, a definition or a permission of the application's services,
     *              which a role bundling a permission for what its parts name is compared with; the engine refuses
     *              a check that reaches such a part with nothing to compare, so a question about anything else
     *              names the empty string
     * @param held  the ids of the roles the user asked about holds in the store, whose bindings the engine reads
     */
    private static CheckRequest checkRequest(RelationshipTuple relationship,
                                             Consistency consistency,
                                             List<RelationshipTuple> context,
                                             String named,
                                             Set<String> held) {
        CheckRequest ret = new CheckRequest().consistency(preference(consistency))
                                             .tupleKey(new CheckRequestTupleKey().user(relationship.user())
                                                                                 .relation(relationship.relation())
                                                                                 ._object(relationship.object()));
        if (!context.isEmpty()) {
            List<TupleKey> contextual = new ArrayList<>();
            for (RelationshipTuple tuple : context) {
                contextual.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
            }
            ret.contextualTuples(new ContextualTupleKeys().tupleKeys(contextual));
        }
        return ret.context(conditionContext(named, held));
    }

    // What the model's conditions compare a tuple with: the definition or service permission asked for, with its
    // hash, and the roles the user asked about holds
    private static Map<String, Object> conditionContext(String named, Set<String> held) {
        Map<String, Boolean> byRole = new TreeMap<>();
        held.forEach(roleId -> byRole.put(roleId, true));
        return Map.of(DefaultAuthzModelGenerator.CHECKED_DEFINITION, named,
                      DefaultAuthzModelGenerator.CHECKED_DEFINITION_HASH, DefaultRolePermissionsService.definitionHash(named),
                      DefaultAuthzModelGenerator.HELD_ROLES, byRole);
    }

    // Asks with the roles the user is known to hold, listed when none are kept. A kept list may predate a grant, so
    // a refusal it led to is followed by a listing, and asked again when the user holds other roles than were kept
    private Future<Boolean> check(String store,
                                  String modelId,
                                  RelationshipTuple asked,
                                  Consistency consistency,
                                  List<RelationshipTuple> context,
                                  String named) {
        Set<String> kept = heldRoles.getIfPresent(heldRolesKey(store, asked.user()));
        Future<Set<String>> held = kept != null ? Future.succeededFuture(kept) : listHeldRoles(store, modelId, asked.user());
        return held.compose(roles -> check(store, modelId, checkRequest(asked, consistency, context, named, roles), asked))
                   .compose(allowed -> {
                       Future<Boolean> ret;
                       if (allowed || kept == null) {
                           ret = Future.succeededFuture(allowed);
                       } else {
                           ret = listHeldRoles(store, modelId, asked.user()).compose(roles -> roles.equals(kept)
                                   ? Future.succeededFuture(false)
                                   : check(store, modelId, checkRequest(asked, consistency, context, named, roles), asked));
                       }
                       return ret;
                   });
    }

    private Future<Boolean> check(String store, String modelId, CheckRequest request, RelationshipTuple asked) {
        return requestCheck(store, modelId, request)
                .recover(e -> onNewestModel(store, modelId, e, newest -> requestCheck(store, newest, request), false, asked));
    }

    private static String heldRolesKey(String store, String user) {
        return store + "|" + user;
    }

    // Lists the roles the user holds on anything in the store, directly or as a member of what a role was granted
    // to, and keeps the list. Everyone together is granted no role, so is listed none
    private Future<Set<String>> listHeldRoles(String store, String modelId, String user) {
        Future<Set<String>> ret;
        if (AuthzConstants.EVERYONE.equals(user)) {
            ret = Future.succeededFuture(Set.of());
        } else {
            // the list a refusal is followed by must hold a grant made a moment before
            ret = listObjects(store, modelId, user, DefaultAuthzModelGenerator.HOLDER, DefaultAuthzModelGenerator.ROLE_HOLDERS,
                              ConsistencyPreference.HIGHER_CONSISTENCY, Set.of())
                    .map(objects -> {
                        Set<String> roles = new TreeSet<>();
                        objects.forEach(object -> roles.add(AuthzNameUtil.idOf(object)));
                        heldRoles.put(heldRolesKey(store, user), roles);
                        return roles;
                    });
        }
        return ret;
    }

    // The engine refuses a request for what it names: a version of the store deleted and re-created under its name
    // since the caller read it, or a relation the version lacked when it was read and the newest has; the newest
    // version is read and answers once
    private <T> Future<T> onNewestModel(String store,
                                        String modelId,
                                        Throwable refusal,
                                        Function<String, Future<T>> request,
                                        T heldByNone,
                                        Object asked) {
        Future<T> ret;
        if (isValidationError(refusal)) {
            ret = stores.refreshModelId(store)
                        .compose(newest -> newest.equals(modelId) ? Future.failedFuture(refusal) : request.apply(newest))
                        // a relation or type the newest version lacks is held by no one: a permission of a type
                        // another store models, or of a service whose model the reconciler has yet to write
                        .recover(refused -> {
                            Future<T> held;
                            if (isValidationError(refused)) {
                                // a relation or type a caller named reaches here as it was sent, and comes back
                                // in the engine's refusal
                                log.warn("The model of store {} cannot answer {}: {}", store,
                                         KinoticUtil.singleLine(String.valueOf(asked)), KinoticUtil.singleLine(refused.getMessage()));
                                held = Future.succeededFuture(heldByNone);
                            } else {
                                held = Future.failedFuture(refused);
                            }
                            return held;
                        });
        } else {
            ret = Future.failedFuture(refusal);
        }
        return ret;
    }

    // A request the engine refused as invalid: the 400 it answers with for a version, type or relation the store
    // lacks. The SDK's FgaApiValidationError also covers the 422 of a request the engine throttled until it timed
    // out, which says nothing about the model
    private static boolean isValidationError(Throwable e) {
        return e instanceof ApiException api && api.getStatusCode() == 400;
    }

    private Future<Boolean> requestCheck(String store, String modelId, CheckRequest request) {
        request.authorizationModelId(modelId);
        return stores.storeIdOf(store).compose(id -> fga.check(id, request)).map(CheckResponse::getAllowed);
    }

    @Override
    public Future<List<String>> listObjects(String store, String user, String relation, String type, Consistency consistency) {
        // a listing has no refusal to follow with a second one, so it names the roles the user holds as they are now
        return stores.modelId(store).compose(modelId -> listHeldRoles(store, modelId, user)
                .compose(held -> listObjects(store, modelId, user, relation, type, preference(consistency), held)));
    }

    private Future<List<String>> listObjects(String store,
                                             String modelId,
                                             String user,
                                             String relation,
                                             String type,
                                             ConsistencyPreference consistency,
                                             Set<String> held) {
        return requestListObjects(store, modelId, user, relation, type, consistency, held)
                .recover(e -> onNewestModel(store, modelId, e,
                                            newest -> requestListObjects(store, newest, user, relation, type, consistency, held),
                                            List.of(), user + " " + relation + " " + type));
    }

    private Future<List<String>> requestListObjects(String store,
                                                    String modelId,
                                                    String user,
                                                    String relation,
                                                    String type,
                                                    ConsistencyPreference consistency,
                                                    Set<String> held) {
        ListObjectsRequest request = new ListObjectsRequest()
                .authorizationModelId(modelId)
                .consistency(consistency)
                .user(user)
                .relation(relation)
                .type(type)
                .context(conditionContext("", held));
        return stores.storeIdOf(store).compose(id -> fga.listObjects(id, request));
    }

    private static ConsistencyPreference preference(Consistency consistency) {
        return consistency == Consistency.HIGHER_CONSISTENCY
                ? ConsistencyPreference.HIGHER_CONSISTENCY
                : ConsistencyPreference.MINIMIZE_LATENCY;
    }
}
