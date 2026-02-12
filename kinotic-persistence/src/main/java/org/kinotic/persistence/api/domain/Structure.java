

package org.kinotic.persistence.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kinotic.rpc.api.crud.Identifiable;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.persistence.api.domain.idl.decorators.EntityType;
import org.kinotic.persistence.api.domain.idl.decorators.MultiTenancyType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Structure implements Identifiable<String> {

    private String id = null; // do not ever set, system managed

    private String name = null;
    /**
     * The id of the application that this structure belongs to.
     * All application ids are unique throughout the entire system.
     */
    private String applicationId = null;

    /**
     * The id of the project that this structure belongs to.
     * All project ids are unique throughout the entire system.
     */
    private String projectId = null;

    private String description = null;

    private MultiTenancyType multiTenancyType = null;

    private EntityType entityType = null;

    private ObjectC3Type entityDefinition = null;

    // TODO: move these to separate metadata class. Especially decoratedProperties since it has nothing to do with the API.
    private Date created = null; // do not ever set, system managed

    private Date updated = null; // do not ever set, system managed

    private boolean published = false; // do not ever set, system managed

    private Date publishedTimestamp = null; // do not ever set, system managed

    private String itemIndex = null; // do not ever set, system managed

    private List<DecoratedProperty> decoratedProperties = new ArrayList<>(); // do not ever set, system managed

    /**
     * The name of the field that will be used for optimistic locking
     * or null if optimistic locking is not enabled
     */
    private String versionFieldName = null; // do not ever set, system managed

    /**
     * The name of the field that will be used to hold the tenant id for an entity.
     * If this is set then Structures will provide "Admin" services to access Entities for multiple tenants.
     */
    private String tenantIdFieldName = null; // do not ever set, system managed

    /**
     * The name of the field that will be used to hold the time reference for an entity.
     */
    private String timeReferenceFieldName = null; // do not ever set, system managed

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Structure structure = (Structure) o;

        return new EqualsBuilder().append(id, structure.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }

    @JsonIgnore
    public boolean isOptimisticLockingEnabled(){
        return versionFieldName != null;
    }

    @JsonIgnore
    public boolean isMultiTenantSelectionEnabled() {
        return tenantIdFieldName != null;
    }

    @JsonIgnore
    public boolean isStream() {
        return timeReferenceFieldName != null;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("applicationId", applicationId)
                .append("projectId", projectId)
                .append("description", description)
                .append("multiTenancyType", multiTenancyType)
                .toString();
    }
}
