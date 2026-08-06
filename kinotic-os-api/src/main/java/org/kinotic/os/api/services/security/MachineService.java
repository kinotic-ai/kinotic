package org.kinotic.os.api.services.security;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineProvisionResult;

import java.util.concurrent.CompletableFuture;

/**
 * Machine-identity management for the caller's organization, used by the web app. A machine
 * is a non-human caller — a daemon or an external client of the org's APIs — that
 * authenticates through the client-credentials grant with the identity's id as
 * {@code client_id} and a secret issued here. Every method derives the organization from the
 * authenticated participant: only org members may call, and only machines belonging to that
 * organization are visible or mutable.
 */
@Publish
public interface MachineService {

    /**
     * Provisions a machine in the caller's organization and returns it together with its
     * generated client secret. The secret is disclosed exactly once — only a hash is stored,
     * so it cannot be retrieved later, only rotated.
     *
     * @param displayName how the machine is listed wherever machines are shown
     */
    CompletableFuture<MachineProvisionResult> createMachine(String displayName);

    /** Lists the machines of the caller's organization, disabled ones included. */
    CompletableFuture<Page<MachineParticipantIdentity>> findMachines(Pageable pageable);

    /**
     * Replaces the client secret of one of the caller's organization's machines, returning the
     * new secret exactly once. The old secret stops working immediately; tokens the machine
     * already holds run out on their own short TTL.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    CompletableFuture<String> rotateSecret(String machineId);

    /**
     * Enables or disables one of the caller's organization's machines. A disabled machine is
     * cut off on its next request — token issuance and every authenticated call alike — and
     * enabling it restores access with the same credential.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    CompletableFuture<Void> setMachineEnabled(String machineId, boolean enabled);

    /**
     * Permanently removes one of the caller's organization's machines, including its stored
     * credential. A removed machine's id cannot authenticate again.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    CompletableFuture<Void> removeMachine(String machineId);
}
