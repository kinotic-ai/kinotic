package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Subject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The relationships held in the stores, and the questions a store answers from them. Every operation names its
 * store the way the store's record is named, {@link AuthzStoreService#SYSTEM} for the system's and an
 * application's id for its own, and the module resolves the name to the engine's store.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface RelationshipService {

    /**
     * Every relationship held on an object.
     *
     * @param store  the store, named as its record is
     * @param object the object, in {@code type:id} form
     * @return the relationships, empty for an object nothing is held on
     */
    Future<List<RelationshipTuple>> read(String store, String object);

    /**
     * Every relationship a user holds on objects of a type, as written.
     *
     * @param store      the store, named as its record is
     * @param user       the user, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param objectType the type of object
     * @return the relationships, empty for a user holding none on the type
     */
    Future<List<RelationshipTuple>> readByUser(String store, String user, String objectType);

    /**
     * Whether the store holds exactly this relationship, as written, without the model's rules.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object
     * @return true when the relationship is held
     */
    Future<Boolean> holds(String store, RelationshipTuple relationship);

    /**
     * Adds the relationships the store does not hold yet, so a write made again after a failure adds nothing
     * twice.
     *
     * @param store         the store, named as its record is
     * @param relationships the relationships that must be held
     * @return completes when every one is held
     */
    Future<Void> ensure(String store, List<RelationshipTuple> relationships);

    /**
     * Removes the relationships the store holds, leaving the ones it does not alone.
     *
     * @param store         the store, named as its record is
     * @param relationships the relationships that must not be held
     * @return completes when none is held
     */
    Future<Void> remove(String store, List<RelationshipTuple> relationships);

    /**
     * Makes the store's roles bundle exactly the given permissions: a permission a role lacks is added, one it
     * holds beyond the given set is removed, and a role already in step is left as it is.
     *
     * @param store the store, named as its record is
     * @param roles each role's id to the model names of the permissions it bundles
     * @return completes when every role is in step
     */
    Future<Void> ensureRoles(String store, Map<String, Set<String>> roles);

    /**
     * Makes the store run the given model with the built-in roles it implies bundling exactly what the model has.
     * The roles are brought in step before the model becomes the store's current version, so a check against
     * the model reads every role it grants through as the model needs it.
     *
     * @param store the store, named as its record is
     * @param model the model the store must run
     * @return the id of the version the store now runs, the one every check names
     */
    Future<String> ensureModelWithRoles(String store, AuthzModel model);

    /**
     * Grants a role to a member on a resource: one binding, attached to the resource, holding the role and the
     * member, through which the member holds every permission the role bundles on the resource and on
     * everything inside it. The engine refuses a role whose home the resource's type may not grant.
     *
     * @param store  the store, named as its record is
     * @param roleId the role granted, {@code <home type>.<name>}, such as a built-in role's id
     * @param member who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object the resource the grant is made on, in {@code type:id} form
     * @return the id of the binding; fails with the engine's validation error when the resource's type may not
     *         grant the role
     */
    Future<String> bind(String store, String roleId, String member, String object);

    /**
     * Makes the binding {@link #bind} would make held under an id the caller chooses, adding whatever of it the
     * store does not hold yet: a grant made again after a failure, or by another server at the same time, adds
     * nothing twice.
     *
     * @param store     the store, named as its record is
     * @param bindingId the binding's id, which {@link #revoke} revokes it by
     * @param roleId    the role granted
     * @param member    who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object    the resource the grant is made on, in {@code type:id} form
     * @return completes when the binding is held
     */
    Future<Void> ensureBound(String store, String bindingId, String roleId, String member, String object);

    /**
     * Takes a resource out of a store: every grant made on it, and every relationship on it, such as its place
     * under its parent and its members. A resource created again under the same id starts with no grant.
     *
     * @param store  the store, named as its record is
     * @param object the resource, in {@code type:id} form
     * @return completes when the store holds nothing of the resource
     */
    Future<Void> removeObject(String store, String object);

    /**
     * Takes a user out of a store: every grant made to it, and every relationship it holds, such as its
     * memberships. A user created again under the same id starts with no grant.
     *
     * @param store the store, named as its record is
     * @param user  the user, in {@code type:id} form
     * @return completes when the store holds nothing of the user, at once for a store that does not exist
     */
    Future<Void> removeSubject(String store, String user);

    /**
     * Revokes a grant where it was made: its role, its members and its attachment to the object are removed, once
     * the store confirms it is attached to the object, so a grant's id guessed from elsewhere revokes nothing.
     *
     * @param store     the store, named as its record is
     * @param bindingId the grant's id, the one {@link #bind} returned
     * @param object    the resource the grant was made on, in {@code type:id} form
     * @return completes when nothing of the binding is held; fails with {@link IllegalArgumentException} when
     *         no grant of the id was made on the object
     */
    Future<Void> revoke(String store, String bindingId, String object);

    /**
     * The grants made on a resource: one per binding attached to it, naming the binding's role and the user
     * or group it was made to. A binding made to any other userset is not a grant and is left out.
     *
     * @param store  the store, named as its record is
     * @param object the resource, in {@code type:id} form
     * @return the grants, empty for a resource nothing is bound on
     */
    Future<List<Grant>> findGrants(String store, String object);

    /**
     * Whether a subject holds a permission on an object, and which of the given grants give it: a grant gives it
     * when its role bundles the permission or one implying it, and it was made to the subject, or to a group the
     * subject is a member of. The answer reflects every write that completed before the call.
     *
     * @param store   the store, named as its record is
     * @param subject the user or group asked about
     * @param holds   the subject's user, the model name of the permission and the object
     * @param context tuples held for this check only, as {@link #check(String, RelationshipTuple, Consistency, List)} takes them
     * @param grants  the grants that reach the object
     * @return whether the permission is held, and the grants giving it as the store holds them
     */
    Future<AccessExplanation> explain(String store,
                                      Subject subject,
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
     * The objects of a type on which the user holds the relation, by the rules of the model the store runs.
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
