package org.kinotic.persistence.api.model;

import lombok.Builder;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;

/**
 * Describes a published entity to the storage layer.
 * This is the node-local view of an entity, holding what is needed to read and write it; the declarative,
 * serializable contract view is {@link EntityDefinition}.
 *
 * @param id the id of the {@link EntityDefinition} this describes
 * @param organizationId the organization the entity belongs to
 * @param applicationId the application the entity belongs to
 * @param name the name of the entity
 * @param itemIndex the Elasticsearch index that items of this entity are stored in
 * @param multiTenancyType how items of this entity are separated between tenants
 * @param tenantIdFieldName the field holding the tenant id, or null if the entity is not tenant selectable
 * @param versionFieldName the field holding the version, or null if optimistic locking is not enabled
 * @param timeReferenceFieldName the field holding the time reference, or null if the entity is not a stream
 *
 * Created by Navíd Mitchell 🤪 on 8/23/26.
 */
@Builder
public record EntityDescriptor(String id,
                               String organizationId,
                               String applicationId,
                               String name,
                               String itemIndex,
                               MultiTenancyType multiTenancyType,
                               String tenantIdFieldName,
                               String versionFieldName,
                               String timeReferenceFieldName) {

    public boolean isOptimisticLockingEnabled(){
        return versionFieldName != null;
    }

    public boolean isMultiTenantSelectionEnabled(){
        return tenantIdFieldName != null;
    }

    public boolean isStream(){
        return timeReferenceFieldName != null;
    }

}
