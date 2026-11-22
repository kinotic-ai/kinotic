package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;

import java.util.List;
import java.util.Set;

/**
 * The questions a store answers from the relationships it holds and the model it runs: whether a subject holds a
 * permission, on what, and through which grants. Every operation names its store the way the store's record is
 * named, {@link AuthzStoreService#SYSTEM} for the system's and an application's id for its own.
 */
public interface AccessCheckService {

    /**
     * Whether a subject holds a permission on an object, and which of the given grants give it: a grant gives it
     * when its role bundles the permission or one implying it, and it was made to the subject, or to a group, a
     * tenant or an application whose members, users or end users the subject is one of. The answer reflects every
     * write that completed before the call.
     *
     * @param store   the store, named as its record is
     * @param model   the model the store runs
     * @param holds   the subject asked about, as {@code user:<id>} or {@code group:<id>#member}, the model name of
     *                the permission and the object
     * @param context tuples held for this check only, as {@link #check(String, RelationshipTuple, Consistency, List)} takes them
     * @param grants  the grants that reach the object
     * @return whether the permission is held, and the grants giving it as the store holds them
     */
    Future<AccessExplanation> explain(String store,
                                      AuthzModel model,
                                      RelationshipTuple holds,
                                      List<RelationshipTuple> context,
                                      List<Grant> grants);

    /**
     * Whether the user holds the relation on the object, directly or through the rules of the model the store runs.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @return true when the relation is held
     */
    default Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency) {
        return check(store, relationship, consistency, List.of());
    }

    /**
     * Whether the user holds the relation on the object, with the given tuples taken as held for this check
     * alone, beside the store's: the edges of an object the store keeps no tuples for.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @param context      tuples held for this check only
     * @return true when the relation is held; false when the store's newest model has no such relation or type
     */
    Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context);

    /**
     * Whether a subject holds a permission of an application's own services on a tenant or on the application,
     * through a role granted there or above: one that holds every such permission, one of the application's own
     * naming this one, or one of the given roles.
     *
     * @param store       the application's store
     * @param user        the subject, in {@code type:id} form
     * @param permission  the model name of the permission, such as {@code report_can_generate}
     * @param object      the tenant or the application, in {@code type:id} form
     * @param roles       the ids of the roles holding the permission by the contracts of the application's
     *                    services, as {@link org.kinotic.authz.api.model.AuthzModel#rolesHolding} gives them
     * @param consistency whether the answer may come from the engine's caches
     * @return true when the subject holds the permission on the object
     */
    Future<Boolean> checkServicePermission(String store, String user, String permission, String object, Set<String> roles, Consistency consistency);

    /**
     * The objects of a type on which the user holds the relation, by the rules of the model the store runs. The
     * future fails with an {@link IllegalStateException} when the engine's listing deadline, three seconds, ended
     * the listing, which may then be missing objects.
     *
     * @param store       the store, named as its record is
     * @param user        who holds the relation
     * @param relation    the relation held
     * @param type        the type of object to list
     * @param consistency how current the answer must be
     * @return the objects, in {@code type:id} form; none when the store's newest model has no such relation or type
     */
    Future<List<String>> listObjects(String store, String user, String relation, String type, Consistency consistency);
}
