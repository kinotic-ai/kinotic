package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.system.api.services.SystemMemberService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultSystemMemberService implements SystemMemberService {

    private final ParticipantIdentityService identityService;

    @Override
    public Future<Page<UserParticipantIdentity>> findUsers(Pageable pageable) {
        return identityService.findUsersByScope(null, null, pageable);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> searchUsers(String searchText, Pageable pageable) {
        return identityService.searchUsersByScope(searchText, null, null, pageable);
    }

    @Override
    public Future<Page<MachineParticipantIdentity>> findMachines(Pageable pageable) {
        return identityService.findMachinesByScope(null, null, pageable);
    }

    @Override
    public Future<MachineProvisionResult> createMachine(String displayName) {
        Validate.notBlank(displayName, "displayName is required");
        // leaving organizationId and applicationId unset is what makes the identity SYSTEM scope
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setDisplayName(displayName);
        return identityService.createMachine(machine);
    }

    @Override
    public Future<String> rotateSecret(String machineId) {
        return loadPlatformMachine(machineId)
                .compose(machine -> identityService.rotateMachineSecret(machine.getId()));
    }

    @Override
    public Future<Void> setMachineEnabled(String machineId, boolean enabled) {
        return loadPlatformMachine(machineId)
                // saveSync so the console's immediate re-query sees the change
                .compose(machine -> identityService.saveSync(machine.setEnabled(enabled)))
                .mapEmpty();
    }

    @Override
    public Future<Void> removeMachine(String machineId) {
        return loadPlatformMachine(machineId)
                // Cascades the IdentityCredential; sync so the console's immediate re-query no
                // longer shows the machine.
                .compose(machine -> identityService.deleteByIdSync(machine.getId()));
    }

    /** Loads a SYSTEM-scope machine for inspection or mutation. */
    private Future<MachineParticipantIdentity> loadPlatformMachine(String machineId) {
        Validate.notBlank(machineId, "machineId is required");
        return identityService.findById(machineId)
                // An organization's machine belongs to its own members, so it is not reachable
                // from here even though a SYSTEM participant may address this service.
                .map(identity -> DomainUtil.requireOwned(identity, MachineParticipantIdentity.class,
                                                        machine -> machine.getOrganizationId() == null,
                                                        "Machine not found."));
    }

}
