package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * The standing deployment of one microservice artifact of a {@link Project}: the VM running
 * it, the machine identity that VM connects as, and what the deployment should be beside what
 * it is. One row per microservice a deployment has ensured; a row outlives the artifact until
 * the deployment is removed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class MicroserviceDeployment implements Reconcilable<DeploymentState>, OrganizationScoped<String> {

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
     * The id of the workload running the microservice, or {@code null} while none has been
     * created.
     */
    private String workloadId;

    /**
     * The id of the machine identity the microservice's workload authenticates as. Its secret
     * is issued once, with the workload it belongs to.
     */
    private String machineIdentityId;

    /**
     * The module the workload was started with, relative to the checkout root: the artifact's
     * directory joined with its entry. A commit that moves the entry point replaces the
     * workload.
     */
    private String entryPoint;

    /**
     * Why the microservice is not running as it should, or {@code null} when it is: the failure
     * of its last deployment, or the exit of a VM that is being started again.
     */
    private String failureMessage;

    /**
     * What the deployment should be, the commit its project's last deployment asked it to run,
     * beside what it is, the phase it is in and the commit it serves, with what the platform
     * keeps on every watched record: that the node running it cannot be reached, and the
     * project deployment it belongs to.
     */
    private ReconcileState<DeploymentState> state = new ReconcileState<>();

    private Date created;

    private Date updated;
}
