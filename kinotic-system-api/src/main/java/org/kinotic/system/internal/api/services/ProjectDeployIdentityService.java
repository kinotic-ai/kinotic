package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Issues the credentials a project's deployment workloads connect to Kinotic with, creating
 * the machine identity the first time a workload needs one and reissuing its secret on every
 * later call. Kinotic keeps only a hash of a machine's secret, so a workload can never be
 * handed the credential an earlier one used: every issue is a rotation, and the secret the
 * previous holder has stops working the moment a new one is issued.
 * <p>
 * The identities are ORGANIZATION scope. Synchronizing entity definitions and publishing
 * services into an application's zone are things the organization does on its own behalf —
 * an APPLICATION-scope identity holds the authority of an end-user of that application, which
 * is not enough to do either.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeployIdentityService {

    private final ParticipantIdentityService identityService;
    private final ProjectDeploymentRepository projectDeploymentRepository;

    /**
     * Issues credentials for the project's sync workload. The returned secret is disclosed only
     * here, and issuing invalidates the secret the previous deployment's sync workload held.
     *
     * @param project the project being deployed
     * @return a future emitting the machine together with its new secret
     */
    public Future<MachineProvisionResult> issueSyncCredentials(Project project) {
        return issue(project, "deploy sync",
                     ProjectDeployment::getSyncMachineId, ProjectDeployment::setSyncMachineId);
    }

    /**
     * Issues credentials for the project's runtime workload, which are only ever issued
     * alongside the workload itself: a later deployment restarts the microservice process
     * inside the running VM, and it reconnects with the secret already in its environment.
     *
     * @param project the project being deployed
     * @return a future emitting the machine together with its new secret
     */
    public Future<MachineProvisionResult> issueRuntimeCredentials(Project project) {
        return issue(project, "runtime",
                     ProjectDeployment::getRuntimeMachineId, ProjectDeployment::setRuntimeMachineId);
    }

    private Future<MachineProvisionResult> issue(Project project,
                                                 String role,
                                                 Function<ProjectDeployment, String> readMachineId,
                                                 BiConsumer<ProjectDeployment, String> writeMachineId) {
        Validate.notNull(project, "project is required");
        return projectDeploymentRepository.findById(project.getId(), project.getOrganizationId())
                .compose(deployment -> {
                    Future<MachineProvisionResult> ret;
                    if (deployment == null) {
                        ret = Future.failedFuture(new IllegalStateException(
                                "No deployment record for project " + project.getId()));
                    } else {
                        ret = issueFor(project, role, deployment, readMachineId, writeMachineId);
                    }
                    return ret;
                });
    }

    private Future<MachineProvisionResult> issueFor(Project project,
                                                    String role,
                                                    ProjectDeployment deployment,
                                                    Function<ProjectDeployment, String> readMachineId,
                                                    BiConsumer<ProjectDeployment, String> writeMachineId) {
        String machineId = readMachineId.apply(deployment);
        Future<MachineProvisionResult> ret;
        if (machineId == null) {
            ret = createAndRecord(project, role, deployment, writeMachineId);
        } else {
            ret = identityService.findById(machineId)
                    .compose(identity -> {
                        Future<MachineProvisionResult> issued;
                        if (identity instanceof MachineParticipantIdentity machine) {
                            issued = identityService.rotateMachineSecret(machineId)
                                                    .map(secret -> new MachineProvisionResult(machine, secret));
                        } else {
                            // an org member may remove a project's machine from the console; the
                            // recorded id then points at nothing and the deployment provisions
                            // a replacement rather than failing
                            issued = createAndRecord(project, role, deployment, writeMachineId);
                        }
                        return issued;
                    });
        }
        return ret;
    }

    /**
     * Records the new machine's id on the deployment before returning it, so a run that fails
     * after this point leaves an identity the next deployment reuses instead of orphaning it.
     */
    private Future<MachineProvisionResult> createAndRecord(Project project,
                                                           String role,
                                                           ProjectDeployment deployment,
                                                           BiConsumer<ProjectDeployment, String> writeMachineId) {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setDisplayName(displayName(project, role))
               .setOrganizationId(project.getOrganizationId());
        return identityService.createMachine(machine)
                .compose(provisioned -> {
                    writeMachineId.accept(deployment, provisioned.machine().getId());
                    deployment.setUpdated(new Date());
                    return projectDeploymentRepository.save(deployment, deployment.getOrganizationId())
                                                      .map(provisioned);
                });
    }

    /** Names the machine for the console listing and for the participant metadata in the logs. */
    private static String displayName(Project project, String role) {
        return (project.getName() != null ? project.getName() : project.getId()) + " " + role;
    }

}
