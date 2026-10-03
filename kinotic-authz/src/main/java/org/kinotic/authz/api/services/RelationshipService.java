package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.RelationshipTuple;

import java.util.List;

/**
 * The relationships held in a store, and the questions a store answers from them.
 */
public interface RelationshipService {

    /**
     * Adds and removes relationships, in as many requests as the engine's limit per request takes. Writing a
     * relationship a store already holds fails, as does deleting one it does not.
     *
     * @param storeId the store
     * @param writes  the relationships to add
     * @param deletes the relationships to remove
     * @return completes when every request succeeded
     */
    Future<Void> write(String storeId, List<RelationshipTuple> writes, List<RelationshipTuple> deletes);

    /**
     * Whether the user holds the relation on the object, directly or through the model's rules.
     *
     * @param storeId      the store
     * @param modelId      the model version to evaluate against
     * @param relationship the user, relation and object to check
     * @return true when the relation is held
     */
    Future<Boolean> check(String storeId, String modelId, RelationshipTuple relationship);

    /**
     * The objects of a type on which the user holds the relation.
     *
     * @param storeId  the store
     * @param modelId  the model version to evaluate against
     * @param user     who holds the relation
     * @param relation the relation held
     * @param type     the type of object to list
     * @return the objects, in {@code type:id} form
     */
    Future<List<String>> listObjects(String storeId, String modelId, String user, String relation, String type);

}
