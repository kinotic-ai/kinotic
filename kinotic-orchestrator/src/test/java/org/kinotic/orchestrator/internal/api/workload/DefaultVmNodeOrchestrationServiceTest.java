package org.kinotic.orchestrator.internal.api.workload;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.workload.WorkloadStatus;
import org.kinotic.domain.api.services.WorkloadService;
import org.kinotic.orchestrator.api.workload.WorkloadStatusReport;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the merge rule of {@link DefaultVmNodeOrchestrationService#reportWorkloadStatus}:
 * node reports move the server's workload record only forward — never onto another node's
 * workload, backwards in time, or onto a workload that no longer exists.
 */
class DefaultVmNodeOrchestrationServiceTest {

    private final FakeWorkloadService workloadService = new FakeWorkloadService();
    private final DefaultVmNodeOrchestrationService service =
            new DefaultVmNodeOrchestrationService(null, null, workloadService);

    private Workload workload(String id, WorkloadStatus status, long updated) {
        Workload workload = new Workload("test", "alpine:latest");
        workload.setId(id)
                .setNodeId("node-1")
                .setStatus(status)
                .setUpdated(new Date(updated));
        workloadService.workloads.put(id, workload);
        return workload;
    }

    private static WorkloadStatusReport report(String workloadId, WorkloadStatus status, long updated) {
        return new WorkloadStatusReport().setWorkloadId(workloadId)
                                         .setStatus(status)
                                         .setUpdated(updated);
    }

    @Test
    void appliesANewerReportFromTheWorkloadsNode() {
        Workload workload = workload("wl-1", WorkloadStatus.RUNNING, 1_000);

        service.reportWorkloadStatus("node-1", List.of(report("wl-1", WorkloadStatus.FAILED, 2_000))).join();

        assertEquals(WorkloadStatus.FAILED, workload.getStatus());
        assertEquals(1, workloadService.saves);
    }

    @Test
    void ignoresAReportOlderThanTheRecordsLastTransition() {
        Workload workload = workload("wl-1", WorkloadStatus.STOPPING, 5_000);

        service.reportWorkloadStatus("node-1", List.of(report("wl-1", WorkloadStatus.RUNNING, 4_000))).join();

        assertEquals(WorkloadStatus.STOPPING, workload.getStatus());
        assertEquals(0, workloadService.saves);
    }

    @Test
    void skipsSavingWhenTheStatusAlreadyMatches() {
        workload("wl-1", WorkloadStatus.RUNNING, 1_000);

        service.reportWorkloadStatus("node-1", List.of(report("wl-1", WorkloadStatus.RUNNING, 2_000))).join();

        assertEquals(0, workloadService.saves);
    }

    @Test
    void ignoresAReportFromANodeTheWorkloadIsNotOn() {
        Workload workload = workload("wl-1", WorkloadStatus.RUNNING, 1_000);

        service.reportWorkloadStatus("node-2", List.of(report("wl-1", WorkloadStatus.FAILED, 2_000))).join();

        assertEquals(WorkloadStatus.RUNNING, workload.getStatus());
        assertEquals(0, workloadService.saves);
    }

    @Test
    void ignoresAReportForADestroyedWorkload() {
        service.reportWorkloadStatus("node-1", List.of(report("wl-gone", WorkloadStatus.STOPPED, 2_000))).join();

        assertEquals(0, workloadService.saves);
    }

    private static class FakeWorkloadService implements WorkloadService {

        final Map<String, Workload> workloads = new HashMap<>();
        int saves = 0;

        @Override
        public CompletableFuture<Workload> findById(String id) {
            return CompletableFuture.completedFuture(workloads.get(id));
        }

        @Override
        public CompletableFuture<Workload> saveSync(Workload entity) {
            saves++;
            // The real service stamps updated on every save in beforeSave
            entity.setUpdated(new Date());
            workloads.put(entity.getId(), entity);
            return CompletableFuture.completedFuture(entity);
        }

        @Override
        public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Long> countForNode(String nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> create(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> createSync(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> save(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Long> count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteByIdSync(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Page<Workload>> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Page<Workload>> search(String searchText, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> syncIndex() {
            throw new UnsupportedOperationException();
        }
    }
}
