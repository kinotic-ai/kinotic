package org.kinotic.management.api.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Caps on what an organization may create while Kinotic Cloud is in beta. A limit left unset
 * imposes no cap, so removing a limit from a deployment's configuration lifts it.
 * Bound under {@code kinotic.managementApi.betaLimits.*} via {@link KinoticManagementApiProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BetaLimitsProperties {

    /**
     * The most applications one organization may have, or {@code null} for no cap.
     */
    @Min(0)
    private Integer maxApplicationsPerOrganization;

    /**
     * The most microservices one application may deploy across all of its projects, or
     * {@code null} for no cap. A deployment whose commit would exceed it fails.
     */
    @Min(0)
    private Integer maxMicroservicesPerApplication;

}
