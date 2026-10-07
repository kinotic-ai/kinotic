package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Subject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The relationships held in the stores, and the questions a store answers from them. Every operation names its
 * store the way the store's record is named, {@link AuthzStoreService#PLATFORM} for the platform's and an
 * application's id for its own, and the module resolves the name to the engine's store.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface RelationshipService {

    /**
     * Adds and removes relationships, in as many requests as the engine's limit per request takes. Adding a
     * relationship the store already holds, or removing one it does not, leaves the store as it is.
     *
     * @param store   the store, named as its record is
     * @param writes  the relationships to add
     * @param deletes the relationships to remove
     * @return completes when every request succeeded
     */
    Future<Void> write(String store, List<RelationshipTuple> writes, List<RelationshipTuple> deletes);

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
     * The built-in roles the store's model defines, as the store's worker wrote them for the model the store
     * runs: each one with the model names of the permissions it bundles.
     *
     * @param store the store, named as its record is
     * @return the roles, in the order of their ids
     */
    Future<List<RoleDefinition>> findRoles(String store);

    /**
     * Grants a role to a member on a resource: one binding, attached to the resource, holding the role and the
     * member, through which the member holds every permission the role bundles on the resource and on
     * everything inside it.
     *
     * @param store  the store, named as its record is
     * @param roleId the role granted, such as a built-in role's id
     * @param member who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object the resource the grant is made on, in {@code type:id} form
     * @return the id of the binding
     */
    Future<String> bind(String store, String roleId, String member, String object);

    /**
     * Makes the binding {@link #bind} would make held under an id the caller chooses, adding whatever of it the
     * store does not hold yet: a grant made again after a failure, or by another server at the same time, adds
     * nothing twice.
     *
     * @param store     the store, named as its record is
     * @param bindingId the binding's id, which {@link #unbind} revokes it by
     * @param roleId    the role granted
     * @param member    who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object    the resource the grant is made on, in {@code type:id} form
     * @return completes when the binding is held
     */
    Future<Void> ensureBound(String store, String bindingId, String roleId, String member, String object);

    /**
     * Revokes a binding: its role, its members and its attachment to the object are removed, leaving nothing of
     * it. A binding already gone leaves nothing to remove.
     *
     * @param store     the store, named as its record is
     * @param bindingId the id {@link #bind} returned
     * @param object    the resource the binding was made on, in {@code type:id} form
     * @return completes when nothing of the binding is held
     */
    Future<Void> unbind(String store, String bindingId, String object);

    /**
     * Revokes a grant where it was made: the binding is removed as {@link #unbind} removes it, once the store
     * confirms it is attached to the object, so a grant's id guessed from elsewhere unbinds nothing.
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
     * Whether a grant gives a subject a permission: the grant's role bundles the permission, and the grant was
     * made to the subject, or to a group the subject is a member of.
     *
     * @param store      the store, named as its record is
     * @param modelId    the model version to evaluate a group's membership against
     * @param grant      the grant
     * @param subject    the user or group asked about
     * @param permission the model name of the permission, such as {@code project_can_edit}
     * @return true when the grant gives it
     */
    Future<Boolean> explains(String store, String modelId, Grant grant, Subject subject, String permission);

    /**
     * Whether the user holds the relation on the object, directly or through the model's rules.
     *
     * @param store        the store, named as its record is
     * @param modelId      the model version to evaluate against
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @return true when the relation is held
     */
    default Future<Boolean> check(String store, String modelId, RelationshipTuple relationship, Consistency consistency) {
        return check(store, modelId, relationship, consistency, List.of());
    }

    /**
     * Whether the user holds the relation on the object, with the given tuples taken as held for this check
     * alone, beside the store's: the edges of an object the store keeps no tuples for.
     *
     * @param store        the store, named as its record is
     * @param modelId      the model version to evaluate against
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @param context      tuples held for this check only
     * @return true when the relation is held
     */
    Future<Boolean> check(String store, String modelId, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context);

    /**
     * The objects of a type on which the user holds the relation.
     *
     * @param store       the store, named as its record is
     * @param modelId     the model version to evaluate against
     * @param user        who holds the relation
     * @param relation    the relation held
     * @param type        the type of object to list
     * @param consistency how current the answer must be
     * @return the objects, in {@code type:id} form
     */
    Future<List<String>> listObjects(String store, String modelId, String user, String relation, String type, Consistency consistency);
}
