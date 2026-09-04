package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.model.UiDeploymentStatusType;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.DeploymentOperationsProxy;
import org.kinotic.management.api.services.UiDeploymentService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUiDeploymentService implements UiDeploymentService {

    /**
     * A site left provisioning this long is no longer being handled by the run that created it
     * or by the provisioner's own polling, so a listing has it checked.
     */
    private static final long STALE_PROVISIONING_MS = 10 * 60_000;

    private final SecurityContext securityContext;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final DeploymentOperationsProxy operations;

    @Override
    public Future<List<UiDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = requireOrgParticipant();
        // rows carry the organization, so a project of another organization lists nothing
        return uiDeploymentRepository.findAllForProject(projectId)
                .compose(deployments -> {
                    List<Future<UiDeployment>> rows = new ArrayList<>();
                    for (UiDeployment deployment : deployments) {
                        if (participant.getOrganizationId().equals(deployment.getOrganizationId())) {
                            rows.add(staleProvisioning(deployment) ? advance(deployment) : Future.succeededFuture(deployment));
                        }
                    }
                    return Future.all(rows).map(all -> all.<UiDeployment>list());
                });
    }

    private static boolean staleProvisioning(UiDeployment deployment) {
        return deployment.getStatus().type() == UiDeploymentStatusType.PROVISIONING
                && deployment.getUpdated() != null
                && deployment.getUpdated().getTime() < System.currentTimeMillis() - STALE_PROVISIONING_MS;
    }

    // The check records itself on the row, so the listings of the next STALE_PROVISIONING_MS
    // list the row without asking again; one that fails leaves the row as it is
    private Future<UiDeployment> advance(UiDeployment deployment) {
        return operations.checkUiSite(deployment.getId())
                .recover(error -> {
                    log.warn("Site {} could not be checked: {}", deployment.getId(), error.getMessage());
                    return Future.succeededFuture(deployment);
                });
    }

    @Override
    public Future<UiDeployment> retryProvisioning(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
                .compose(deployment -> operations.provisionUiSite(deployment.getId()));
    }

    @Override
    public Future<Void> remove(String deploymentId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwned(deploymentId, participant)
                .compose(deployment -> operations.removeUiSite(deployment.getId()));
    }

    /** Loads a deployment of the participant's organization; another organization's is indistinguishable from none. */
    private Future<UiDeployment> loadOwned(String deploymentId, OrganizationParticipant participant) {
        Validate.notBlank(deploymentId, "deploymentId is required");
        return uiDeploymentRepository.findById(deploymentId)
                .map(deployment -> DomainUtil.requireOwned(deployment, participant.getOrganizationId(), "UI deployment not found."));
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

}
