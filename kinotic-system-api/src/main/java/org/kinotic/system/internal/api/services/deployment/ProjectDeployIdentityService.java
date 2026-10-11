package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
 * is not enough to do either. The sync workload's identity is a {@link MachineKind#PROJECT_SYNC}
 * machine, a microservice's an {@link MachineKind#APP_RUNTIME} one.
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
                        ret = issue(newMachine(project, MachineKind.PROJECT_SYNC, "deploy sync"), project, deployment.getSyncMachineIdentityId(),
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
        return issue(newMachine(project, MachineKind.APP_RUNTIME, "runtime " + deployment.getName()), project, deployment.getMachineIdentityId(), identityId -> {
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
        return ret.compose(result -> bind(result.machine().getId(), unsaved.getMachineKind(), project).map(result));
    }

    /**
     * Records the new machine's id before returning it, so a run that fails after this point
     * leaves an identity the next deployment reuses instead of orphaning it.
     */
    private Future<MachineProvisionResult> createAndRecord(MachineParticipantIdentity unsaved,
                                                           Function<String, Future<Void>> recordIdentity) {
        return identityService.createMachine(unsaved)
                .compose(provisioned -> recordIdentity.apply(provisioned.machine().getId()).map(provisioned));
    }

    /**
     * Binds the machine to what its workload does, each role under a binding id of the machine's own, so issuing
     * credentials binds a machine recorded before as well as a new one, and binds each role once. Both workloads
     * act as an editor of the project, which records its artifacts and runs its migrations. The sync workload
     * creates, saves and publishes the application's entity definitions and their named queries, as
     * {@code entity_definition.editor} on the application may. A runtime registers the application's services, as
     * {@code application.runtime} may, and works on the rows of every definition of the application, as
     * {@code entity_definition.admin} on the application may.
     */
    private Future<Void> bind(String machineId, MachineKind kind, Project project) {
        String machine = AuthzUtil.object(AuthzUtil.USER_TYPE, machineId);
        String projectObject = AuthzUtil.object(ProjectService.RESOURCE_TYPE,
                                                DomainUtil.authzId(ProjectService.RESOURCE_TYPE, project.getOrganizationId(), project.getId()));
        String application = AuthzUtil.object(AuthzUtil.APPLICATION_TYPE,
                                              DomainUtil.authzApplicationId(project.getOrganizationId(), project.getApplicationId()));
        List<Map.Entry<String, String>> grants = new ArrayList<>();
        grants.add(Map.entry(AuthzUtil.roleId(ProjectService.RESOURCE_TYPE, AuthzUtil.EDITOR), projectObject));
        if (kind == MachineKind.APP_RUNTIME) {
            grants.add(Map.entry(AuthzUtil.APPLICATION_RUNTIME_ROLE, application));
            grants.add(Map.entry(AuthzUtil.roleId(AuthzUtil.ENTITY_DEFINITION_TYPE, AuthzUtil.ADMIN), application));
        } else {
            grants.add(Map.entry(AuthzUtil.roleId(AuthzUtil.ENTITY_DEFINITION_TYPE, AuthzUtil.EDITOR), application));
        }
        Future<Void> ret = Future.succeededFuture();
        for (Map.Entry<String, String> grant : grants) {
            String roleId = grant.getKey();
            ret = ret.compose(v -> relationships.ensureBound(AuthzStoreService.PLATFORM, roleId + "-of-" + machineId,
                                                             roleId, machine, grant.getValue()));
        }
        return ret;
    }

    /**
     * The machine a workload of the project is created with when it has none, named for the console
     * listing and for the participant metadata in the logs.
     */
    private static MachineParticipantIdentity newMachine(Project project, MachineKind kind, String role) {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setMachineKind(kind)
               .setDisplayName((project.getName() != null ? project.getName() : project.getId()) + " " + role)
               .setOrganizationId(project.getOrganizationId());
        return machine;
    }

}
