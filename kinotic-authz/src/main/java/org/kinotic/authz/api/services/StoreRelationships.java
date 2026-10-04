package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.RelationshipTuple;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The relationships held in one store, and the questions the store answers from them. A handle from
 * {@link RelationshipService}, which names the store so its callers never hold a store id.
 */
public interface StoreRelationships {
    /**
     * Adds and removes relationships, in as many requests as the engine's limit per request takes. Writing a
     * relationship the store already holds fails, as does deleting one it does not; {@link #ensure} and
     * {@link #remove} are the idempotent forms.
     *
     * @param writes  the relationships to add
     * @param deletes the relationships to remove
     * @return completes when every request succeeded
     */
    Future<Void> write(List<RelationshipTuple> writes, List<RelationshipTuple> deletes);

    /**
     * Every relationship held on an object.
     *
     * @param object the object, in {@code type:id} form
     * @return the relationships, empty for an object nothing is held on
     */
    Future<List<RelationshipTuple>> read(String object);

    /**
     * Whether the store holds exactly this relationship, as written, without the model's rules.
     *
     * @param relationship the user, relation and object
     * @return true when the relationship is held
     */
    Future<Boolean> holds(RelationshipTuple relationship);

    /**
     * Adds the relationships the store does not hold yet, so a write made again after a failure adds nothing
     * twice.
     *
     * @param relationships the relationships that must be held
     * @return completes when every one is held
     */
    Future<Void> ensure(List<RelationshipTuple> relationships);

    /**
     * Removes the relationships the store holds, leaving the ones it does not alone.
     *
     * @param relationships the relationships that must not be held
     * @return completes when none is held
     */
    Future<Void> remove(List<RelationshipTuple> relationships);

    /**
     * Makes the store's roles bundle exactly the given permissions: a permission a role lacks is added, one it
     * holds beyond the given set is removed, and a role already in step is left as it is.
     *
     * @param roles each role's id to the model names of the permissions it bundles
     * @return completes when every role is in step
     */
    Future<Void> ensureRoles(Map<String, Set<String>> roles);

    /**
     * Grants a role to a member on a resource: one binding, attached to the resource, holding the role and the
     * member, through which the member holds every permission the role bundles on the resource and on
     * everything inside it.
     *
     * @param roleId the role granted, such as a built-in role's id
     * @param member who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object the resource the grant is made on, in {@code type:id} form
     * @return the id of the binding
     */
    Future<String> bind(String roleId, String member, String object);

    /**
     * Whether the user holds the relation on the object, directly or through the model's rules.
     *
     * @param modelId      the model version to evaluate against
     * @param relationship the user, relation and object to check
     * @return true when the relation is held
     */
    Future<Boolean> check(String modelId, RelationshipTuple relationship);

    /**
     * The objects of a type on which the user holds the relation.
     *
     * @param modelId  the model version to evaluate against
     * @param user     who holds the relation
     * @param relation the relation held
     * @param type     the type of object to list
     * @return the objects, in {@code type:id} form
     */
    Future<List<String>> listObjects(String modelId, String user, String relation, String type);
}
