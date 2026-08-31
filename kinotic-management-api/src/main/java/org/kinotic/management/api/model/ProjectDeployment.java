package org.kinotic.management.api.model;

import org.kinotic.domain.api.model.ApplicationScoped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Records where a {@link Project}'s code is deployed: the node holding the checkout, the
 * long-lived workload serving it, and the commit currently live. One row per project;
 * {@link #id} equals the project id. Absence of a row means the project has never been
 * deployed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ProjectDeployment implements ApplicationScoped<String> {

    /**
     * The id of the deployment, always equal to the id of the deployed project.
     */
    private String id;

    private String organizationId;

    private String applicationId;

    /**
     * The id of the node hosting the project's checkout directory and runtime workload.
     */
    private String nodeId;

    /**
     * Absolute path on the node of the host directory holding the project's checkout.
     * The sync workload mounts it read-write; the runtime workload mounts it read-only.
     */
    private String hostDir;

    /**
     * The id of the long-lived workload running the project's microservices, or
     * {@code null} while the first deployment is still in progress.
     */
    private String runtimeWorkloadId;

    /**
     * The id of the machine identity the sync workload authenticates as, or {@code null}
     * before the project's first deployment. Its secret is reissued for every deployment.
     */
    private String syncMachineIdentityId;

    /**
     * The id of the machine identity the runtime workload authenticates as, or {@code null}
     * while the first deployment is still in progress. Its secret is issued once, with the
     * workload it belongs to.
     */
    private String runtimeMachineIdentityId;

    /**
     * Sha of the last commit successfully synced to the node.
     */
    private String commitSha;

    /**
     * The id of the most recent deployment job run for this project.
     */
    private String lastJobRunId;

    private ProjectDeploymentStatus status;

    private Date created;

    private Date updated;

}
