package org.kinotic.persistence.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.utils.DomainUtil;

/**
 * {@link EntityHolder} holds the data and the id for an entity
 * Created by Navíd Mitchell 🤪 on 5/11/23.
 */
public record EntityHolder<T>(T entity, String id, MultiTenancyType multiTenancyType, String tenantId, String version) {

    public EntityHolder {
        Validate.notNull(entity, "entity cannot be null");
        Validate.notBlank(id, "id cannot be null or blank");
        Validate.notNull(multiTenancyType, "multiTenancyType cannot be null");
        if(multiTenancyType == MultiTenancyType.SHARED) {
           Validate.notBlank(tenantId, "tenantId cannot be null or blank for shared multi tenancy");
        }
    }

    public String getDocumentId() {
        return DomainUtil.createEntityDocumentId(multiTenancyType, tenantId, id);
    }

    public boolean isElasticVersionPresent(){
        // For the initial save we require a null or empty string
        return version != null && !version.isEmpty();
    }

    public ElasticVersion getElasticVersionIfPresent() {
        if (!isElasticVersionPresent()) {
            return null;
        }
        String[] parts = version.split(":");
        if(parts.length != 2){
            throw new IllegalArgumentException("Invalid version format: " + version);
        }
        return new ElasticVersion(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

}
