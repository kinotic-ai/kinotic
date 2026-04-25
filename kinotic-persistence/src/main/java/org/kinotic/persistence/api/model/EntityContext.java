package org.kinotic.persistence.api.model;

import org.kinotic.core.api.security.Participant;

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
     * @return the {@link Participant} that is performing the operation
     */
    Participant getParticipant();

    /**
     * Checks if a tenant selection is provided for the current operation
     *
     * @return true if a tenant selection is provided, false otherwise
     */
    boolean hasTenantSelection();

    /**
     * Gets the tenant selection for the current operation
     * NOTE: This should only be set if {@link EntityDefinition#isMultiTenantSelectionEnabled()} is true
     *
     * @return the lists of tenants that data is being requested for
     */
    List<String> getTenantSelection();

    /**
     * Sets the tenant selection for the current operation
     * NOTE: This should only be set if {@link EntityDefinition#isMultiTenantSelectionEnabled()} is true
     *
     * @param tenantSelection the lists of tenants that data is being requested for
     */
    EntityContext setTenantSelection(List<String> tenantSelection);

}
