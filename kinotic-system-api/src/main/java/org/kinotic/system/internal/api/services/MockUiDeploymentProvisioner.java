package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link UiDeploymentProvisioner} used when site provisioning is disabled
 * ({@code kinotic.systemApi.uiDeployment.disableProvisioner=true}). Nothing serves, and
 * every deployment reads ready at once so publishing completes in development and tests
 * without Front Door.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.systemApi.uiDeployment.disableProvisioner", havingValue = "true")
public class MockUiDeploymentProvisioner implements UiDeploymentProvisioner {

    @Override
    public Future<DeploymentStatus> check(UiDeployment deployment, String commitSha) {
        log.debug("MockUiDeploymentProvisioner reads site {} ready", deployment.getId());
        return Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.READY));
    }

}
