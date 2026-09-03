package org.kinotic.persistence.api.model;

import org.kinotic.domain.api.model.security.participant.ScopedParticipant;

import java.util.List;

/**
 * Holds information for all "Entity" related operations.
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public interface EntityContext {

    /**
     * If defined, this will restrict the response to only include the fields listed here.
     *
     * @return a list of included fields, if {@link List} is empty no fields will be included, if null all fields will be included.
     */
    List<String> getIncludedFieldsFilter();

    /**
     * Returns whether there is an included fields filter defined.
     *
     * @return true if an included fields filter is defined, false otherwise
     */
    boolean hasIncludedFieldsFilter();

    /**
     * The id of the Organization that owns the data this operation reads or writes. Every
     * {@link EntityDefinition} belongs to one Organization, so this is never null.
     *
     * @return the id of the Organization this operation is carried out within
     */
    String getOrganizationId();

    /**
     * The {@link ScopedParticipant} performing the operation. An Application's end users reach
     * entity data as Application participants, and the console reaches it as an Organization
     * participant, so both scopes appear here.
     *
     * @return the {@link ScopedParticipant} that is performing the operation
     */
    ScopedParticipant getParticipant();

    /**
     * The tenant slice of an Application's end-user data this operation is confined to, or null
     * when the participant carries no tenant. An {@link EntityDefinition} whose
     * {@link org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType} is
     * {@code SHARED} requires one.
     *
     * @return the tenant this operation is confined to, or null
     */
    String getTenantId();

    /**
     * The tenant this operation is confined to, for an operation that cannot be carried out
     * without one — every read and write of an {@link EntityDefinition} whose
     * {@link org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType} is
     * {@code SHARED}.
     *
     * @return the tenant this operation is confined to; never null
     * @throws IllegalStateException if the participant carries no tenant
     */
    default String requireTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("This operation requires a Participant with a TenantId");
        }
        return tenantId;
    }

    /**
     * Checks if a tenant selection is provided for the current operation
     *
     * @return true if a tenant selection is provided, false otherwise
     */
    boolean hasTenantSelection();

    /**
     * Gets the tenant selection for the current operation
     * NOTE: This should only be set if multi-tenant selection is enabled for the {@link EntityDefinition}
     *
     * @return the lists of tenants that data is being requested for
     */
    List<String> getTenantSelection();

    /**
     * Sets the tenant selection for the current operation
     * NOTE: This should only be set if multi-tenant selection is enabled for the {@link EntityDefinition}
     *
     * @param tenantSelection the lists of tenants that data is being requested for
     */
    EntityContext setTenantSelection(List<String> tenantSelection);

}
