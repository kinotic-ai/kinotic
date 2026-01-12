package org.mindignited.structures.api.domain;

import org.kinotic.continuum.api.security.Participant;

import java.util.List;

/**
 * Contains information about the security context of the current operation
 */
public interface SecurityContext {

    /**
     * @return the {@link Participant} that is performing the operation
     */
    Participant getParticipant();

    /**
     * Checks if a tenant selection is provided for the current operation
     * @return true if a tenant selection is provided, false otherwise
     */
    boolean hasTenantSelection();

    /**
     * Gets the tenant selection for the current operation
     * NOTE: This should only be set if {@link Structure#isMultiTenantSelectionEnabled()} is true
     *
     * @return the lists of tenants that data is being requested for
     */
    List<String> getTenantSelection();

    /**
     * Sets the tenant selection for the current operation
     * NOTE: This should only be set if {@link Structure#isMultiTenantSelectionEnabled()} is true
     *
     * @param tenantSelection the lists of tenants that data is being requested for
     */
    SecurityContext setTenantSelection(List<String> tenantSelection);

}
