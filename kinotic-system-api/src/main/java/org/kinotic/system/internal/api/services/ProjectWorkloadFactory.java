package org.kinotic.system.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.deployment.DeployTarget;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the workloads a project runs on its node: the foreground sync workload of a deployment
 * run, and the long-lived runtime workload of one microservice. Each connects to Kinotic as the
 * machine whose credentials it is given, sized by {@link ProjectWorkloadSizes}.
 */
@Component
@RequiredArgsConstructor
public class ProjectWorkloadFactory {

    private final KinoticSystemApiProperties properties;
    private final KinoticDomainProperties domainProperties;

    /**
     * The sync workload of a deployment run: fetches the commit into the checkout mounted at
     * {@code /workspace}, synchronizes the project and builds its UIs.
     */
    public Workload sync(Project project,
                         DeployTarget target,
                         ProjectRepoToken token,
                         MachineProvisionResult credentials,
                         String commitSha) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-sync-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setId(target.syncWorkloadId());
        workload.setDescription("Checkout and entity sync for project " + project.getId());
        workload.getState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, project.getOrganizationId(), project.getId()));
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setDetached(false);
        workload.setCpus(ProjectWorkloadSizes.SYNC_CPUS);
        workload.setMemoryMb(ProjectWorkloadSizes.SYNC_MEMORY_MB);
        workload.setDiskSizeMb(ProjectWorkloadSizes.SYNC_DISK_SIZE_MB);
        workload.setEntrypoint(List.of("bun", "src/sync.ts"));
        workload.getEnvironment().put("GIT_CLONE_URL", token.getCloneUrl());
        workload.getEnvironment().put("GIT_REF", commitSha);
        workload.getEnvironment().put("KINOTIC_PROJECT_ID", project.getId());
        // The UIs are built against the address a browser reaches the platform on, which the
        // egress address in DeploymentProperties.serverHost is not
        workload.getEnvironment().put("KINOTIC_UI_SERVER_URL", domainProperties.getDomain().resolveApiBaseUrl());
        putKinoticConnection(workload, deployment, credentials);
        workload.getSecrets().put("GIT_TOKEN", token.getToken());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setSizeLimitMb(ProjectWorkloadSizes.SYNC_MOUNT_LIMIT_MB));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getSyncAllowedHosts(), deployment));
        return workload;
    }

    /**
     * The runtime workload of one microservice: serves {@code entryPoint} from the checkout
     * mounted read-only at {@code /app} on the node holding it, belonging to the microservice's
     * deployment.
     */
    public Workload runtime(Project project,
                            String nodeId,
                            String hostDir,
                            MicroserviceDeployment microservice,
                            String entryPoint,
                            MachineProvisionResult credentials) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId() + "-" + microservice.getName(),
                                         deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice " + microservice.getName() + " of project " + project.getId());
        workload.getState().setParent(new WatchedParent(WatchedType.MICROSERVICE_DEPLOYMENT, microservice.getOrganizationId(), microservice.getId()));
        workload.setNodeId(nodeId);
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setCpus(ProjectWorkloadSizes.RUNTIME_CPUS);
        workload.setMemoryMb(ProjectWorkloadSizes.RUNTIME_MEMORY_MB);
        workload.setDiskSizeMb(ProjectWorkloadSizes.RUNTIME_DISK_SIZE_MB);
        workload.getEnvironment().put("KINOTIC_APP_ENTRY", entryPoint);
        // The project's microservices export their traces and metrics through the node, grouped
        // under the project's name; the sync and publish workloads are steps of the run and
        // export nothing
        workload.setTelemetry(true);
        workload.getEnvironment().put("OTEL_SERVICE_NAME", project.getName());
        putKinoticConnection(workload, deployment, credentials);
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(hostDir)
                                                        .setGuestPath("/app")
                                                        .setReadOnly(true));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getRuntimeAllowedHosts(), deployment));
        return workload;
    }

    /**
     * Configures how the workload reaches Kinotic and who it connects as. Requires the
     * workload's {@code organizationId} to already be set.
     */
    private static void putKinoticConnection(Workload workload,
                                             DeploymentProperties deployment,
                                             MachineProvisionResult credentials) {
        workload.getEnvironment().put("KINOTIC_SERVER_HOST", deployment.getServerHost());
        workload.getEnvironment().put("KINOTIC_SERVER_PORT", String.valueOf(deployment.getServerPort()));
        workload.getEnvironment().put("KINOTIC_SERVER_USE_SSL", String.valueOf(deployment.isServerUseSsl()));
        workload.getEnvironment().put("KINOTIC_ORGANIZATION_ID", workload.getOrganizationId());
        workload.getEnvironment().put("KINOTIC_CLIENT_ID", credentials.machine().getId());
        // a workload's environment is persisted verbatim and readable by anyone who can read
        // the workload back, so the secret travels as a secret, which the node injects into
        // the guest and never stores
        workload.getSecrets().put("KINOTIC_CLIENT_SECRET", credentials.clientSecret());
    }

    private static List<String> allowedHosts(List<String> workloadHosts, DeploymentProperties deployment) {
        List<String> hosts = new ArrayList<>(workloadHosts);
        hosts.add(deployment.getServerHost());
        return hosts;
    }

    private DeploymentProperties deployment() {
        return properties.getSystemApi().getDeployment();
    }
}
