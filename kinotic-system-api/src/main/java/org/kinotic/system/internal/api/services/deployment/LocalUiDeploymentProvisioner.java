package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.deployment.DeploymentStatus;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.system.api.services.deployment.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * {@link UiDeploymentProvisioner} of a machine's own site storage: every deployment reads ready at once. With
 * {@code AZURITE} storage the local edge reads a site's files from the emulator on every request, so a site
 * serves its commit as soon as its upload finished; with {@code DISABLED} storage nothing serves.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${kinotic.systemApi.uiDeployment.siteStorage:AZURE}'.toUpperCase() != 'AZURE'")
public class LocalUiDeploymentProvisioner implements UiDeploymentProvisioner {

    @Override
    public Future<DeploymentStatus> check(UiDeployment deployment, String commitSha) {
        log.debug("LocalUiDeploymentProvisioner reads site {} ready", deployment.getId());
        return Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.READY));
    }

}
