package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;

/**
 * The system's own members: the system users who sign in to the system console, and the
 * SYSTEM-scope machines that connect as system daemons, such as a worker node's vm-manager.
 * Published in the system zone, which only SYSTEM participants may address; every method works
 * on SYSTEM scope alone, so an organization's users and machines stay that organization's to
 * manage through {@code MachineService}.
 */
@Publish
public interface SystemMemberService {

    /**
     * Returns the system users.
     *
     * @param pageable the page settings to use
     * @return a page of system users
     */
    Future<Page<SystemParticipantIdentity>> findUsers(Pageable pageable);

    /**
     * Searches the system users by email and display name.
     *
     * @param searchText the text to match system users against
     * @param pageable the page settings to use
     * @return a page of matching system users
     */
    Future<Page<SystemParticipantIdentity>> searchUsers(String searchText, Pageable pageable);

    /**
     * Lists the system's machines, disabled ones included.
     *
     * @param pageable the page settings to use
     * @return a page of SYSTEM-scope {@link MachineParticipantIdentity}s
     */
    Future<Page<MachineParticipantIdentity>> findMachines(Pageable pageable);

    /**
     * Provisions a system machine and returns it together with its generated client secret.
     * The secret is disclosed exactly once — only a hash is stored, so it cannot be retrieved
     * later.
     *
     * @param displayName how the machine is listed wherever machines are shown
     * @return the provisioned machine and its one-time secret
     */
    Future<MachineProvisionResult> createMachine(String displayName);

    /**
     * Replaces the client secret of a system machine, returning the new secret exactly once.
     * The old secret stops working immediately; a connection the machine already holds lasts
     * until it disconnects.
     *
     * @param machineId a SYSTEM-scope machine
     * @return the new secret in plaintext, shown exactly once
     */
    Future<String> rotateSecret(String machineId);

    /**
     * Enables or disables a system machine. A disabled machine is cut off on its next
     * connection, and enabling it restores access with the same credential.
     *
     * @param machineId a SYSTEM-scope machine
     */
    Future<Void> setMachineEnabled(String machineId, boolean enabled);

    /**
     * Permanently removes a system machine, including its stored credential. A removed
     * machine's id cannot authenticate again.
     *
     * @param machineId a SYSTEM-scope machine
     */
    Future<Void> removeMachine(String machineId);

}
