package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.model.UiDeploymentStatus;
import org.kinotic.management.api.model.UiDeploymentStatusType;
import org.kinotic.management.api.services.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link UiDeploymentProvisioner} used when site provisioning is disabled
 * ({@code kinotic.managementApi.uiDeployment.disableProvisioner=true}). Serves nothing, and
 * marks every deployment ready at once so publishing completes in development and tests
 * without Front Door.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.uiDeployment.disableProvisioner", havingValue = "true")
public class MockUiDeploymentProvisioner implements UiDeploymentProvisioner {

    @Override
    public Future<Void> prepareOrganization(Organization organization) {
        return Future.succeededFuture();
    }

    @Override
    public Future<UiDeployment> provision(UiDeployment deployment, Organization organization) {
        log.debug("MockUiDeploymentProvisioner marked site {} ready", deployment.getId());
        return Future.succeededFuture(deployment.setStatus(new UiDeploymentStatus(UiDeploymentStatusType.READY)));
    }

    @Override
    public Future<UiDeployment> checkProvisioning(UiDeployment deployment) {
        return Future.succeededFuture(deployment.setStatus(new UiDeploymentStatus(UiDeploymentStatusType.READY)));
    }

    @Override
    public Future<Void> remove(UiDeployment deployment) {
        return Future.succeededFuture();
    }

}
