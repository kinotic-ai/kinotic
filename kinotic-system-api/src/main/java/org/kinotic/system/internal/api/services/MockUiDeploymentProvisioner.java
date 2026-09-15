package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link UiDeploymentProvisioner} used when site provisioning is disabled
 * ({@code kinotic.systemApi.uiDeployment.disableProvisioner=true}). Nothing serves, and
 * every deployment is marked ready at once so publishing completes in development and tests
 * without Front Door.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.systemApi.uiDeployment.disableProvisioner", havingValue = "true")
public class MockUiDeploymentProvisioner implements UiDeploymentProvisioner {

    @Override
    public Future<UiDeployment> provision(UiDeployment deployment) {
        log.debug("MockUiDeploymentProvisioner marked site {} ready", deployment.getId());
        return Future.succeededFuture(deployment.setStatus(new DeploymentStatus(DeploymentStatusType.READY)));
    }

    @Override
    public Future<UiDeployment> checkProvisioning(UiDeployment deployment) {
        return Future.succeededFuture(deployment.setStatus(new DeploymentStatus(DeploymentStatusType.READY)));
    }

}
