package org.kinotic.domain.api.model.security.identity;

import org.apache.commons.lang3.Validate;

/**
 * Why a {@link MachineParticipantIdentity} exists. Every purpose other than
 * {@link #API_ACCESS} is platform-managed: the platform provisions and rotates the machine,
 * and it is not editable through the portal.
 */
public enum MachinePurpose {

    /**
     * An API client an organization member provisioned; the portal manages it.
     */
    API_ACCESS(null),

    /**
     * Authenticates a project's sync workload; its secret rotates with every deploy of the
     * project named by {@code purposeId}.
     */
    PROJECT_DEPLOY("project-deploy-"),

    /**
     * Authenticates a project's runtime workload, hosting the services of the project named
     * by {@code purposeId}; its secret is minted together with the workload it is baked into.
     */
    PROJECT_RUNTIME("project-runtime-"),

    /**
     * Authenticates a vm-manager node in the system zone.
     */
    NODE_AGENT(null);

    private final String machineIdPrefix;

    MachinePurpose(String machineIdPrefix) {
        this.machineIdPrefix = machineIdPrefix;
    }

    /**
     * The deterministic machine id for a purpose provisioned per resource, so provisioning
     * upserts the same identity for a resource's whole life and cleanup can derive the id
     * without a lookup.
     *
     * @param resourceId id of the resource the machine serves - the project id
     * @return the machine id
     * @throws IllegalStateException for purposes whose machine ids are not derived
     */
    public String machineId(String resourceId) {
        Validate.notBlank(resourceId, "resourceId must not be blank");
        if (machineIdPrefix == null) {
            throw new IllegalStateException(name() + " machines do not have derived ids");
        }
        return machineIdPrefix + resourceId;
    }

}
