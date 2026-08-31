package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.security.MachineService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class DefaultMachineService implements MachineService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final ApplicationRepository applicationRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;

    @Override
    public Future<MachineProvisionResult> createMachine(String displayName, String applicationId) {
        Validate.notBlank(displayName, "displayName is required");
        OrganizationParticipant participant = requireOrgParticipant();
        return applicationRepository.requireById(applicationId, participant.getOrganizationId())
                .compose(app -> {
                    MachineParticipantIdentity machine = new MachineParticipantIdentity();
                    machine.setDisplayName(displayName)
                           .setOrganizationId(participant.getOrganizationId())
                           .setApplicationId(applicationId);
                    return identityService.createMachine(machine);
                });
    }

    @Override
    public Future<Page<MachineParticipantIdentity>> findMachines(String applicationId, Pageable pageable) {
        Validate.notBlank(applicationId, "applicationId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // scope-composed query: a foreign or unknown application matches nothing
        return identityService.findMachinesByScope(participant.getOrganizationId(), applicationId, pageable);
    }

    @Override
    public Future<List<MachineParticipantIdentity>> findProjectMachines(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // The deployment record is stored under the organization, so loading it with the
        // caller's organization is both the ownership check and the lookup — a project of
        // another organization is indistinguishable from one that has never deployed.
        return projectDeploymentRepository.findById(projectId, participant.getOrganizationId())
                .compose(deployment -> {
                    Future<List<MachineParticipantIdentity>> ret;
                    if (deployment == null) {
                        ret = Future.succeededFuture(List.of());
                    } else {
                        ret = loadMachines(deployment.getSyncMachineIdentityId(), deployment.getRuntimeMachineIdentityId());
                    }
                    return ret;
                });
    }

    /** Resolves recorded machine ids, skipping any whose identity an org member has since removed. */
    private Future<List<MachineParticipantIdentity>> loadMachines(String syncMachineIdentityId, String runtimeMachineIdentityId) {
        List<Future<ParticipantIdentity>> lookups = Stream.of(syncMachineIdentityId, runtimeMachineIdentityId)
                                                          .filter(Objects::nonNull)
                                                          .map(identityService::findById)
                                                          .toList();
        return Future.all(lookups)
                     .map(composite -> composite.<ParticipantIdentity>list().stream()
                                                .filter(MachineParticipantIdentity.class::isInstance)
                                                .map(MachineParticipantIdentity.class::cast)
                                                .toList());
    }

    @Override
    public Future<String> rotateSecret(String machineId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                .compose(machine -> identityService.rotateMachineSecret(machine.getId()));
    }

    @Override
    public Future<Void> setMachineEnabled(String machineId, boolean enabled) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                // saveSync so the console's immediate re-query sees the change
                .compose(machine -> identityService.saveSync(machine.setEnabled(enabled)))
                .mapEmpty();
    }

    @Override
    public Future<Void> removeMachine(String machineId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                // Cascades the IdentityCredential; sync so the console's immediate re-query
                // no longer shows the machine.
                .compose(machine -> identityService.deleteByIdSync(machine.getId()));
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    /** Loads a machine of the participant's organization for inspection or mutation. */
    private Future<MachineParticipantIdentity> loadOwnedMachine(String machineId, OrganizationParticipant participant) {
        Validate.notBlank(machineId, "machineId is required");
        return identityService.findById(machineId)
                .map(identity -> DomainUtil.requireOwned(identity, MachineParticipantIdentity.class,
                        machine -> participant.getOrganizationId().equals(machine.getOrganizationId()),
                        "Machine not found."));
    }
}
