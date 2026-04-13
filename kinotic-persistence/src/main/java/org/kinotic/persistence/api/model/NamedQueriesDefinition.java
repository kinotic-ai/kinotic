package org.kinotic.persistence.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.os.api.model.OrganizationScoped;

import java.util.List;

/**
 * Provides Metadata that represents Named Queries for an Application
 * Created by Navíd Mitchell 🤪 on 3/18/24.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class NamedQueriesDefinition implements OrganizationScoped<String> {

    private String id = null;

    private String organizationId = null;

    /**
     * The id of the application that this EntityDefinition belongs to.
     * All application ids are unique throughout the entire system.
     */
    private String applicationId = null;

    /**
     * The id of the project that this EntityDefinition belongs to.
     * All project ids are unique throughout the entire system.
     */
    private String projectId = null;

    private String entityDefinitionName = null;

    private List<FunctionDefinition> namedQueries = null;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        NamedQueriesDefinition namedQueriesDefinition = (NamedQueriesDefinition) o;

        return new EqualsBuilder().append(id, namedQueriesDefinition.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("applicationId", applicationId)
                .append("projectId", projectId)
                .append("entityDefinitionName", entityDefinitionName)
                .toString();
    }

    @Override
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
