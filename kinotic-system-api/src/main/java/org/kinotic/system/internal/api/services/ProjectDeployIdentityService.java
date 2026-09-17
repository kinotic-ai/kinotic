package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
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
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;

    /**
     * Issues credentials for the project's sync workload. The returned secret is disclosed only
     * here, and issuing invalidates the secret the previous deployment's sync workload held.
     *
     * @param project the project being deployed
     * @return a future emitting the machine together with its new secret
     */
    public Future<MachineProvisionResult> issueSyncCredentials(Project project) {
        Validate.notNull(project, "project is required");
        return projectDeploymentRepository.findById(project.getId(), project.getOrganizationId())
                .compose(deployment -> {
                    Future<MachineProvisionResult> ret;
                    if (deployment == null) {
                        ret = Future.failedFuture(new IllegalStateException(
                                "No deployment record for project " + project.getId()));
                    } else {
                        ret = issue(project, "deploy sync", deployment.getSyncMachineIdentityId(), identityId -> {
                            deployment.setSyncMachineIdentityId(identityId).setUpdated(new Date());
                            return projectDeploymentRepository.save(deployment, deployment.getOrganizationId()).mapEmpty();
                        });
                    }
                    return ret;
                });
    }

    /**
     * Issues credentials for the runtime workload of one of the project's microservices, which
     * are only ever issued alongside the workload itself: a later deployment restarts the
     * microservice process inside the running VM, and it reconnects with the secret already in
     * its environment. Records the machine's id on the given deployment.
     *
     * @param project    the project being deployed
     * @param deployment the microservice's deployment, already persisted
     * @return a future emitting the machine together with its new secret
     */
    public Future<MachineProvisionResult> issueRuntimeCredentials(Project project, MicroserviceDeployment deployment) {
        Validate.notNull(project, "project is required");
        Validate.notNull(deployment, "deployment is required");
        return issue(project, "runtime " + deployment.getName(), deployment.getMachineIdentityId(), identityId -> {
            deployment.setMachineIdentityId(identityId).setUpdated(new Date());
            return microserviceDeploymentRepository.save(deployment).mapEmpty();
        });
    }

    private Future<MachineProvisionResult> issue(Project project,
                                                 String role,
                                                 String identityId,
                                                 Function<String, Future<Void>> recordIdentity) {
        Future<MachineProvisionResult> ret;
        if (identityId == null) {
            ret = createAndRecord(project, role, recordIdentity);
        } else {
            ret = identityService.findById(identityId)
                    .compose(identity -> {
                        Future<MachineProvisionResult> issued;
                        if (identity instanceof MachineParticipantIdentity machine) {
                            issued = identityService.rotateMachineSecret(identityId)
                                                    .map(secret -> new MachineProvisionResult(machine, secret));
                        } else {
                            // an org member may remove a project's machine from the console; the
                            // recorded id then points at nothing and the deployment provisions
                            // a replacement rather than failing
                            issued = createAndRecord(project, role, recordIdentity);
                        }
                        return issued;
                    });
        }
        return ret;
    }

    /**
     * Records the new machine's id before returning it, so a run that fails after this point
     * leaves an identity the next deployment reuses instead of orphaning it.
     */
    private Future<MachineProvisionResult> createAndRecord(Project project,
                                                           String role,
                                                           Function<String, Future<Void>> recordIdentity) {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setDisplayName(displayName(project, role))
               .setOrganizationId(project.getOrganizationId());
        return identityService.createMachine(machine)
                .compose(provisioned -> recordIdentity.apply(provisioned.machine().getId())
                                                      .map(provisioned));
    }

    /** Names the machine for the console listing and for the participant metadata in the logs. */
    private static String displayName(Project project, String role) {
        return (project.getName() != null ? project.getName() : project.getId()) + " " + role;
    }

}
