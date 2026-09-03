package org.kinotic.management.api.model;

import org.kinotic.domain.api.model.ApplicationScoped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Records where a {@link Project}'s code is deployed: the node holding the checkout, the sync
 * workload and identity of its deployments, the artifacts of the synced commit, and the commit
 * currently live. The microservices themselves are recorded one per
 * {@link MicroserviceDeployment}. One row per project; {@link #id} equals the project id.
 * Absence of a row means the project has never been deployed.
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
     * The id of the node hosting the project's checkout directory and every workload of its
     * deployments.
     */
    private String nodeId;

    /**
     * Absolute path on the node of the host directory holding the project's checkout.
     * The sync workload mounts it read-write; the runtime workloads mount it read-only.
     */
    private String hostDir;

    /**
     * The id of the sync workload of the most recent deployment run, kept with its logs
     * until the next run retires it, or {@code null} before the first run resolved its
     * target.
     */
    private String syncWorkloadId;

    /**
     * The id of the machine identity the sync workload authenticates as, or {@code null}
     * before the project's first deployment. Its secret is reissued for every deployment.
     */
    private String syncMachineIdentityId;

    /**
     * Sha of the last commit successfully synced to the node.
     */
    private String commitSha;

    /**
     * The artifacts the sync workload found in the checkout of {@link #artifactsCommitSha},
     * or {@code null} before a sync has reported any.
     */
    private ProjectArtifacts artifacts;

    /**
     * Sha of the commit {@link #artifacts} were found in.
     */
    private String artifactsCommitSha;

    /**
     * The id of the most recent deployment job run for this project.
     */
    private String lastJobRunId;

    private ProjectDeploymentStatus status;

    private Date created;

    private Date updated;

}
