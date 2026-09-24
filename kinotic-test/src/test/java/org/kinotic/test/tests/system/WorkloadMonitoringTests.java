package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.services.WorkloadMonitoringService;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Exercises what an organization reads of its workloads against a real Elasticsearch index: the
 * organization and application filters have to agree with the mapping for a workload to come
 * back, and another organization's workloads and the platform's own must never be among them.
 */
@SpringBootTest
public class WorkloadMonitoringTests extends KinoticTestBase {

    private static final String OTHER_ORG_ID = "globex";
    private static final String OTHER_APP_ID = "monitoring-other-app";

    @Autowired
    private WorkloadService workloadService;

    @Autowired
    private WorkloadMonitoringService workloadMonitoringService;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    public void removeCreatedWorkloads() throws Exception {
        for (String id : created) {
            await(workloadService.deleteById(id));
        }
        created.clear();
        await(workloadService.syncIndex());
    }

    @Test
    public void listsTheOrganizationsWorkloadsAcrossItsApplications() throws Exception {
        Workload ours = workload("monitoring-ours", TEST_ORG_ID, TEST_APP_ID);
        Workload oursInAnotherApp = workload("monitoring-ours-other-app", TEST_ORG_ID, OTHER_APP_ID);
        Workload theirs = workload("monitoring-theirs", OTHER_ORG_ID, TEST_APP_ID);
        Workload platform = workload("monitoring-platform", null, null);
        indexWorkloads();

        Set<String> ids = idsOf(await(runAsOrganization(() -> workloadMonitoringService.findWorkloads(Pageable.ofSize(500)))));

        Assertions.assertTrue(ids.contains(ours.getId()));
        Assertions.assertTrue(ids.contains(oursInAnotherApp.getId()));
        Assertions.assertFalse(ids.contains(theirs.getId()), "another organization's workload must not be listed");
        Assertions.assertFalse(ids.contains(platform.getId()), "a platform workload must not be listed");
    }

    @Test
    public void narrowsToOneApplicationOfTheOrganization() throws Exception {
        Workload ours = workload("monitoring-app-ours", TEST_ORG_ID, TEST_APP_ID);
        Workload oursInAnotherApp = workload("monitoring-app-other-app", TEST_ORG_ID, OTHER_APP_ID);
        // the same application id under another organization belongs to that organization alone
        Workload theirsSameAppId = workload("monitoring-app-theirs", OTHER_ORG_ID, TEST_APP_ID);
        indexWorkloads();

        Set<String> ids = idsOf(await(runAsOrganization(
                () -> workloadMonitoringService.findWorkloadsForApplication(TEST_APP_ID, Pageable.ofSize(500)))));

        Assertions.assertTrue(ids.contains(ours.getId()));
        Assertions.assertFalse(ids.contains(oursInAnotherApp.getId()), "another application's workload must not be listed");
        Assertions.assertFalse(ids.contains(theirsSameAppId.getId()), "another organization's workload must not be listed");
    }

    @Test
    public void findsItsOwnWorkloadButNotAnotherOrganizationsOrThePlatforms() throws Exception {
        Workload ours = workload("monitoring-find-ours", TEST_ORG_ID, TEST_APP_ID);
        Workload theirs = workload("monitoring-find-theirs", OTHER_ORG_ID, null);
        Workload platform = workload("monitoring-find-platform", null, null);

        Workload found = await(runAsOrganization(() -> workloadMonitoringService.findWorkload(ours.getId())));
        Assertions.assertEquals(ours.getId(), found.getId());
        Assertions.assertEquals(WorkloadStatus.RUNNING, found.getStatus());

        // another organization's workload and a missing one fail alike, so the reply is no existence oracle
        Assertions.assertEquals("Workload not found.", failureOf(theirs.getId()).getMessage());
        Assertions.assertEquals("Workload not found.", failureOf(platform.getId()).getMessage());
        Assertions.assertEquals("Workload not found.", failureOf("monitoring-find-missing").getMessage());
    }

    private Workload workload(String name, String organizationId, String applicationId) throws Exception {
        Workload workload = new Workload(name, "alpine:latest");
        workload.setOrganizationId(organizationId);
        workload.setApplicationId(applicationId);
        workload.setStatus(WorkloadStatus.RUNNING);
        Workload saved = await(workloadService.save(workload));
        created.add(saved.getId());
        return saved;
    }

    private Throwable failureOf(String workloadId) throws Exception {
        try {
            await(runAsOrganization(() -> workloadMonitoringService.findWorkload(workloadId)));
            return Assertions.fail("finding " + workloadId + " should have failed");
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private static Set<String> idsOf(Page<Workload> page) {
        return page.getContent().stream().map(Workload::getId).collect(Collectors.toSet());
    }

    /** Individual saves skip the refresh; one index sync afterwards makes the whole fixture searchable. */
    private void indexWorkloads() throws Exception {
        await(workloadService.syncIndex());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
