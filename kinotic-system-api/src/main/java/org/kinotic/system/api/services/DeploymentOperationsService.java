package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.UiDeployment;

/**
 * The operations on the platform's infrastructure behind the management plane's deployment
 * services: a microservice's VM, and a UI's site and files. Published in the system zone, where the management server reaches it through
 * {@code DeploymentOperationsProxy}. Callers are trusted: the management plane authorizes a
 * request before it reaches this service, which checks nothing about the caller.
 */
@Publish
public interface DeploymentOperationsService {

    /**
     * Restarts the microservice's VM in place: a running VM is stopped and booted again with
     * its disk intact, a stopped or failed one is booted again. Fails when the deployment has
     * no VM, which the next deployment of the project resolves.
     *
     * @param deploymentId the microservice deployment
     * @return a future completing once the VM is booting again
     */
    Future<Void> restartMicroservice(String deploymentId);

    /**
     * Removes the deployment: destroys the microservice's VM, removes its machine identity,
     * and deletes the record. What is already gone is not a failure.
     *
     * @param deploymentId the microservice deployment
     * @return a future completing when everything is gone
     */
    Future<Void> removeMicroservice(String deploymentId);

    /**
     * Advances a provisioning site through its provisioner and records what it found.
     *
     * @param deploymentId the UI deployment
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> checkUiSite(String deploymentId);

    /**
     * Provisions the site again, completing whatever an earlier attempt left missing, and
     * records the outcome.
     *
     * @param deploymentId the UI deployment
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> provisionUiSite(String deploymentId);

    /**
     * Takes the site down, deletes the UI's published files, and deletes the record. What is
     * already gone is not a failure.
     *
     * @param deploymentId the UI deployment
     * @return a future completing when everything is gone
     */
    Future<Void> removeUiSite(String deploymentId);

}
