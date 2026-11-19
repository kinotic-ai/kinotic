package org.kinotic.system.internal.api.services;

import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.Participant;
import org.apache.commons.lang3.StringUtils;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.MachineType;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.system.api.services.SystemMemberService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultSystemMemberService implements SystemMemberService {

    // the bytes of a generated password, 24 characters once encoded
    private static final int PASSWORD_BYTES = 18;

    private final ParticipantIdentityService identityService;
    private final PasswordConfigurationService passwordConfigurationService;
    private final SecurityContext securityContext;

    @Override
    public Future<Page<SystemParticipantIdentity>> findUsers(Pageable pageable) {
        return identityService.findSystemUsers(pageable);
    }

    @Override
    public Future<Page<SystemParticipantIdentity>> searchUsers(String searchText, Pageable pageable) {
        return identityService.searchSystemUsers(searchText, pageable);
    }

    @Override
    public Future<SystemParticipantIdentity> addUser(String email, String displayName) {
        Validate.notBlank(email, "email is required");
        SystemParticipantIdentity user = new SystemParticipantIdentity();
        user.setEmail(email.trim())
            .setPending(true)
            .setDisplayName(StringUtils.trimToNull(displayName));
        return identityService.createUser(user, null);
    }

    @Override
    public Future<Void> setUserEnabled(String userId, boolean enabled) {
        Validate.isTrue(!securityContext.requireParticipant(Participant.class).getId().equals(userId),
                        "You cannot perform this action on yourself.");
        return loadSystemUser(userId)
                // saveSync so the console's immediate re-query sees the change
                .compose(user -> identityService.saveSync(user.setEnabled(enabled)))
                .mapEmpty();
    }

    @Override
    public Future<String> resetUserPassword(String userId) {
        String password = DomainUtil.generateUrlSafeToken(PASSWORD_BYTES);
        return passwordConfigurationService.findOffered(DomainUtil.SYSTEM_ID, null, null)
                                           .compose(offered -> {
                                               Validate.isTrue(offered != null, "The system console's login takes no password.");
                                               return loadSystemUser(userId);
                                           })
                                           .compose(user -> identityService.setPassword(user.getId(), password))
                                           .map(v -> password);
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
        machine.setMachineType(MachineType.CLIENT)
               .setDisplayName(displayName);
        return identityService.createMachine(machine);
    }

    @Override
    public Future<String> rotateSecret(String machineId) {
        return loadSystemMachine(machineId)
                .compose(machine -> identityService.rotateMachineSecret(machine.getId()));
    }

    @Override
    public Future<Void> setMachineEnabled(String machineId, boolean enabled) {
        return loadSystemMachine(machineId)
                // saveSync so the console's immediate re-query sees the change
                .compose(machine -> identityService.saveSync(machine.setEnabled(enabled)))
                .mapEmpty();
    }

    @Override
    public Future<Void> removeMachine(String machineId) {
        return loadSystemMachine(machineId)
                // Cascades the IdentityCredential; sync so the console's immediate re-query no
                // longer shows the machine.
                .compose(machine -> identityService.deleteByIdSync(machine.getId()));
    }

    /** Loads a system user for mutation. */
    private Future<SystemParticipantIdentity> loadSystemUser(String userId) {
        Validate.notBlank(userId, "userId is required");
        return identityService.findById(userId)
                              .map(identity -> DomainUtil.requireOwned(identity, SystemParticipantIdentity.class,
                                                                      user -> true, "User not found."));
    }

    /** Loads a SYSTEM-scope machine for inspection or mutation. */
    private Future<MachineParticipantIdentity> loadSystemMachine(String machineId) {
        Validate.notBlank(machineId, "machineId is required");
        return identityService.findById(machineId)
                // An organization's machine belongs to its own members, so it is not reachable
                // from here even though a SYSTEM participant may address this service.
                .map(identity -> DomainUtil.requireOwned(identity, MachineParticipantIdentity.class,
                                                        machine -> machine.getOrganizationId() == null,
                                                        "Machine not found."));
    }

}
