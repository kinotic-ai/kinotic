package org.kinotic.os.internal.api.services.security;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.os.api.services.security.MachineService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultMachineService implements MachineService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final ApplicationRepository applicationRepository;

    @Override
    public CompletableFuture<MachineProvisionResult> createMachine(String displayName, String applicationId) {
        Validate.notBlank(displayName, "displayName is required");
        OrganizationParticipant participant = requireOrgParticipant();
        return applicationRepository.requireById(applicationId, participant.getOrganizationId())
                .toCompletionStage().toCompletableFuture()
                .thenCompose(app -> {
                    MachineParticipantIdentity machine = new MachineParticipantIdentity();
                    machine.setDisplayName(displayName)
                           .setOrganizationId(participant.getOrganizationId())
                           .setApplicationId(applicationId);
                    return identityService.createMachine(machine)
                                          .toCompletionStage().toCompletableFuture();
                });
    }

    @Override
    public CompletableFuture<Page<MachineParticipantIdentity>> findMachines(String applicationId, Pageable pageable) {
        Validate.notBlank(applicationId, "applicationId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // scope-composed query: a foreign or unknown application matches nothing
        return identityService.findMachinesByScope(participant.getOrganizationId(), applicationId, pageable)
                              .toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<String> rotateSecret(String machineId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                .thenCompose(machine -> identityService.rotateMachineSecret(machine.getId())
                                                       .toCompletionStage().toCompletableFuture());
    }

    @Override
    public CompletableFuture<Void> setMachineEnabled(String machineId, boolean enabled) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                // saveSync so the console's immediate re-query sees the change
                .thenCompose(machine -> identityService.saveSync(machine.setEnabled(enabled))
                                                       .toCompletionStage().toCompletableFuture())
                .thenApply(m -> null);
    }

    @Override
    public CompletableFuture<Void> removeMachine(String machineId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMachine(machineId, participant)
                // Cascades the IdentityCredential; sync so the console's immediate re-query
                // no longer shows the machine.
                .thenCompose(machine -> identityService.deleteByIdSync(machine.getId())
                                                       .toCompletionStage().toCompletableFuture());
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    /** Loads a machine of the participant's organization for inspection or mutation. */
    private CompletableFuture<MachineParticipantIdentity> loadOwnedMachine(String machineId, OrganizationParticipant participant) {
        Validate.notBlank(machineId, "machineId is required");
        return identityService.findById(machineId)
                .toCompletionStage().toCompletableFuture()
                .thenApply(identity -> DomainUtil.requireOwned(identity, MachineParticipantIdentity.class,
                        machine -> participant.getOrganizationId().equals(machine.getOrganizationId()),
                        "Machine not found."));
    }
}
