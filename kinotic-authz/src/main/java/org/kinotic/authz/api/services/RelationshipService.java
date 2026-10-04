package org.kinotic.authz.api.services;

/**
 * The relationships held in the stores: the platform's, which nothing outside the authorization module names,
 * and an application's, named by its id.
 */
public interface RelationshipService {
    /**
     * @return the relationships of the platform store
     */
    StoreRelationships platform();

    /**
     * @param storeId an application's store
     * @return the relationships of that store
     */
    StoreRelationships store(String storeId);
}
