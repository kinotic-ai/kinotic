package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The relationships held in the stores, and the questions a store answers from them. Every operation names its
 * store the way the store's record is named, {@link AuthzStoreService#PLATFORM} for the platform's and an
 * application's id for its own, and the module resolves the name to the engine's store.
 */
public interface RelationshipService {

    /**
     * Adds and removes relationships, in as many requests as the engine's limit per request takes. Writing a
     * relationship the store already holds fails, as does deleting one it does not; {@link #ensure} and
     * {@link #remove} are the idempotent forms.
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
     * Whether the user holds the relation on the object, directly or through the model's rules.
     *
     * @param store        the store, named as its record is
     * @param modelId      the model version to evaluate against
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @return true when the relation is held
     */
    Future<Boolean> check(String store, String modelId, RelationshipTuple relationship, Consistency consistency);

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
