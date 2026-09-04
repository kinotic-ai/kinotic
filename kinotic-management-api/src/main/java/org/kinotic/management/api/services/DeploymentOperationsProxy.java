package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.UiDeployment;

/**
 * The management server's way to the system server's {@code DeploymentOperationsService}:
 * the one path from the management plane to the infrastructure behind its deployment
 * services. Method for method the same contract; the management plane authorizes a request
 * before making a call, since the service trusts its callers.
 */
@Proxy(namespace = "org.kinotic.system.api.services", name = "DeploymentOperationsService")
@Zone(DomainUtil.SYSTEM_API_ZONE)
public interface DeploymentOperationsProxy {

    Future<Void> restartMicroservice(String deploymentId);

    Future<Void> removeMicroservice(String deploymentId);

    Future<UiDeployment> checkUiSite(String deploymentId);

    Future<UiDeployment> provisionUiSite(String deploymentId);

    Future<Void> removeUiSite(String deploymentId);

    Future<Organization> provisionOrganization(String organizationId);

}
