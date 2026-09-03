package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * The standing deployment of one microservice artifact of a {@link Project}: the VM running
 * it, the machine identity that VM connects as, the commit it was last ensured for, and its
 * status. One row per microservice a deployment has ensured; a row outlives the artifact until
 * the deployment is removed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class MicroserviceDeployment implements Identifiable<String> {

    /**
     * Unique id of the deployment.
     */
    private String id;

    private String organizationId;

    private String applicationId;

    /**
     * The id of the project the microservice belongs to.
     */
    private String projectId;

    /**
     * The microservice's identity: the {@link MicroserviceArtifact#name()} it was deployed
     * from. Unique among the project's microservice deployments.
     */
    private String name;

    /**
     * The id of the workload running the microservice, or {@code null} when the deployment
     * could not create one.
     */
    private String workloadId;

    /**
     * The id of the machine identity the microservice's workload authenticates as. Its secret
     * is issued once, with the workload it belongs to.
     */
    private String machineIdentityId;

    /**
     * The module the workload was started with, relative to the checkout root: the artifact's
     * directory joined with its entry. A commit that moves the entry replaces the workload.
     */
    private String entry;

    /**
     * Sha of the commit the deployment was last ensured for.
     */
    private String commitSha;

    private MicroserviceDeploymentStatus status;

    private Date created;

    private Date updated;

}
