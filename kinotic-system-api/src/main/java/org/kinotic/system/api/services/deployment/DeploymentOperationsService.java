package org.kinotic.system.api.services.deployment;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;

/**
 * The operations on the platform's infrastructure behind the management plane's deployment
 * services: a microservice's VM, and a UI's site and files. Published in the system zone, where the management server reaches it through
 * {@code DeploymentOperationsProxy}. Callers are trusted: the management plane authorizes a
 * request before it reaches this service, which checks nothing about the caller.
 */
@Publish
public interface DeploymentOperationsService {

    /**
     * Runs the microservice in a fresh VM: a VM still running is stopped, and its deployment's worker
     * replaces it once the run has ended; a deployment without a running VM has its intent renewed,
     * so the worker deploys it again. Fails when the project has never been deployed.
     *
     * @param deploymentId the microservice deployment
     * @return a future completing once the restart is asked for
     */
    Future<Void> restartMicroservice(String deploymentId);

    /**
     * Asks for the deployment's removal: its worker stops the microservice's VM, removes its machine
     * identity, and deletes the record. What is already gone is not a failure.
     *
     * @param deploymentId the microservice deployment
     * @return a future completing when the removal is asked for
     */
    Future<Void> removeMicroservice(String deploymentId);

    /**
     * Asks for the deployment's removal: its worker takes the site down, deletes the UI's published
     * files, and deletes the record. What is already gone is not a failure.
     *
     * @param deploymentId the UI deployment
     * @return a future completing when the removal is asked for
     */
    Future<Void> removeUiSite(String deploymentId);

}
