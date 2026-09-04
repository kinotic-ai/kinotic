package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationProvisioner;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.kinotic.management.api.services.UiDeploymentProvisioner;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Gives an organization what its deployments publish to, as a recorded job: a task that
 * provisions its storage, then one that prepares what the serving layer needs to read that
 * storage. Both take minutes on Azure, so the job runs in the background from the moment it
 * is started and every task records its outcome on the organization; the run itself is
 * recorded as the organization's {@link Organization#getProvisioningJobRunId() provisioning
 * run}, where the console shows it. Both tasks are idempotent, so a run started again does
 * what an earlier one left undone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOrganizationProvisioner implements OrganizationProvisioner {

    private final JobService jobService;
    private final OrganizationService organizationService;
    private final OrganizationStorageProvisioner organizationStorageProvisioner;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;

    @Override
    public Future<Void> provision(Organization organization) {
        String organizationId = organization.getId();
        JobDefinition definition = JobDefinition.create("Provision organization " + organizationId)
                .name("provision-organization-" + organizationId)
                .version("1.0.0")
                .task(Tasks.fromCallable("Provision storage",
                                         () -> organizationStorageProvisioner.ensureStorage(organizationId)
                                                                             .<Void>mapEmpty()
                                                                             .toCompletionStage().toCompletableFuture()))
                // read again: the storage task saved the organization it works from
                .task(Tasks.fromCallable("Prepare Front Door",
                                         () -> organizationService.findById(organizationId)
                                                                  .compose(uiDeploymentProvisioner::prepareOrganization)
                                                                  .toCompletionStage().toCompletableFuture()));
        JobRunHandle handle = jobService.run(definition, JobOwner.ofOrganization(organizationId, null));
        organization.setProvisioningJobRunId(handle.getJobRunId()).setUpdated(new Date());
        // the run starts once its completion is subscribed, so the run id is on the record
        // before the first task saves the organization
        return organizationService.save(organization)
                .onSuccess(saved -> handle.completion()
                        .onSuccess(v -> log.info("Organization {} is provisioned", organizationId))
                        .onFailure(error -> log.warn("Provisioning organization {} failed, see job run {}: {}",
                                                     organizationId, handle.getJobRunId(), error.getMessage())))
                .mapEmpty();
    }

}
