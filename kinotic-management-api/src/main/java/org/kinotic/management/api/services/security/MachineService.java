package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.management.api.model.ProjectDeployment;

import java.util.List;

/**
 * Machine-identity management for the applications and projects of the caller's organization,
 * used by the web app. A machine is a non-human caller that connects through the Kinotic
 * client with the identity's id as {@code clientId} and a secret issued here. An API client
 * of one application acts with that application's scope, exactly the position an application
 * end-user occupies; the machines the platform provisions for a project's deployment act with
 * the organization's scope. Every method derives the organization from the authenticated
 * participant: only org members may call, the application or project must belong to that
 * organization, and only its machines are visible or mutable.
 */
@Publish
public interface MachineService {

    /**
     * Provisions a machine for an application of the caller's organization and returns it
     * together with its generated client secret. The secret is disclosed exactly once — only
     * a hash is stored, so it cannot be retrieved later, only rotated.
     *
     * @param displayName   how the machine is listed wherever machines are shown
     * @param applicationId the application whose API the machine calls; must belong to the
     *                      caller's organization
     */
    Future<MachineProvisionResult> createMachine(String displayName, String applicationId);

    /** Lists the machines of the given application of the caller's organization, disabled ones included. */
    Future<Page<MachineParticipantIdentity>> findMachines(String applicationId, Pageable pageable);

    /**
     * Lists the machines the deployment of one of the caller's organization's projects has
     * provisioned for its workloads, in the order {@link ProjectDeployment} records them —
     * the sync workload's, then the runtime workload's. They are created and their secrets
     * reissued by the deployment itself; this listing is how the organization's members see
     * that they exist. A project that has never deployed has none.
     *
     * @param projectId a project belonging to the caller's organization
     */
    Future<List<MachineParticipantIdentity>> findProjectMachines(String projectId);

    /**
     * Replaces the client secret of one of the caller's organization's machines, returning the
     * new secret exactly once. The old secret stops working immediately; a connection the
     * machine already holds lasts until it disconnects.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    Future<String> rotateSecret(String machineId);

    /**
     * Enables or disables one of the caller's organization's machines. A disabled machine is
     * cut off on its next connection, and enabling it restores access with the same
     * credential.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    Future<Void> setMachineEnabled(String machineId, boolean enabled);

    /**
     * Permanently removes one of the caller's organization's machines, including its stored
     * credential. A removed machine's id cannot authenticate again.
     *
     * @param machineId a machine belonging to the caller's organization
     */
    Future<Void> removeMachine(String machineId);
}
