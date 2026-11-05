package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.domain.api.model.security.identity.MachineType;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
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
 * is not enough to do either. The sync workload's identity is a {@link MachineType#PROJECT_SYNC}
 * machine, a microservice's an {@link MachineType#APP_RUNTIME} one. Every issue grants the machine what its workload
 * does: both edit the project; the sync workload edits the application's entity definitions, and a runtime is the
 * application's runtime, so it may register and host that application's services and no other's, and works on the
 * rows of the application's definitions.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeployIdentityService {

    private final ParticipantIdentityService identityService;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final RelationshipService relationships;

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
                        ret = issue(newMachine(project, MachineType.PROJECT_SYNC, "deploy sync"), project, deployment.getSyncMachineIdentityId(),
                                    identityId -> projectDeploymentRepository.recordSyncMachine(project.getId(), project.getOrganizationId(), identityId));
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
        return issue(newMachine(project, MachineType.APP_RUNTIME, "runtime " + deployment.getName()), project, deployment.getMachineIdentityId(), identityId -> {
            deployment.setMachineIdentityId(identityId);
            return microserviceDeploymentRepository.recordMachine(deployment.getId(), identityId);
        });
    }

    private Future<MachineProvisionResult> issue(MachineParticipantIdentity unsaved,
                                                 Project project,
                                                 String identityId,
                                                 Function<String, Future<Void>> recordIdentity) {
        Future<MachineProvisionResult> ret;
        if (identityId == null) {
            ret = createAndRecord(unsaved, recordIdentity);
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
                            issued = createAndRecord(unsaved, recordIdentity);
                        }
                        return issued;
                    });
        }
        // bound on every issue, so a machine reused from an earlier deployment holds the grants as a new one does
        return ret.compose(result -> bind(result.machine().getId(), unsaved.getMachineType(), project).map(result));
    }

    /**
     * Records the new machine's id before returning it, so a run that fails after this point
     * leaves an identity the next deployment reuses instead of orphaning it.
     */
    private Future<MachineProvisionResult> createAndRecord(MachineParticipantIdentity unsaved,
                                                           Function<String, Future<Void>> recordIdentity) {
        return identityService.createMachine(unsaved)
                .compose(provisioned -> recordIdentity.apply(provisioned.machine().getId())
                                                      .map(provisioned));
    }

    // Each role is bound under a binding id of the machine's own, so issuing credentials again binds each role once
    private Future<Void> bind(String machineId, MachineType machineType, Project project) {
        String machine = AuthzUtil.object(AuthzConstants.USER_TYPE, machineId);
        String projectObject = AuthzGraphUtil.authzObject(project.getOrganizationId(), new Resource(AuthzConstants.PROJECT_TYPE, project.getId()));
        String application = AuthzGraphUtil.authzApplication(project.getOrganizationId(), project.getApplicationId());
        // each role to the object it is granted on
        Map<String, String> grants = new LinkedHashMap<>();
        grants.put(AuthzUtil.roleId(AuthzConstants.PROJECT_TYPE, AuthzConstants.EDITOR), projectObject);
        if (machineType == MachineType.APP_RUNTIME) {
            grants.put(AuthzConstants.APPLICATION_RUNTIME_ROLE, application);
            grants.put(AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.ADMIN), application);
        } else {
            grants.put(AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR), application);
        }
        Future<Void> ret = Future.succeededFuture();
        for (Map.Entry<String, String> grant : grants.entrySet()) {
            String roleId = grant.getKey();
            ret = ret.compose(v -> relationships.ensureBound(AuthzStoreService.SYSTEM, AuthzUtil.roleBindingIdOf(roleId, machineId),
                                                             roleId, machine, grant.getValue()));
        }
        return ret;
    }

    /**
     * The machine a workload of the project is created with when it has none, named for the console
     * listing and for the participant metadata in the logs.
     */
    private static MachineParticipantIdentity newMachine(Project project, MachineType machineType, String role) {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setMachineType(machineType)
               .setOrganizationId(project.getOrganizationId())
               .setDisplayName((project.getName() != null ? project.getName() : project.getId()) + " " + role);
        return machine;
    }

}
