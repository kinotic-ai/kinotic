package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;

/**
 * The workloads the platform runs on behalf of the caller's organization: the long-lived VM of
 * each project microservice, and the one-off runs that sync its projects and publish or remove
 * their UIs. A workload whose run has ended keeps its record, with the status and exit code the
 * run ended on.
 * A workload's logs are read through {@link LogService}, its traces and metrics through
 * {@link TelemetryService}.
 */
@Publish
public interface WorkloadMonitoringService {

    /**
     * Finds the workloads of the caller's organization.
     *
     * @param pageable the page of workloads to return
     * @return a future emitting the page of workloads
     */
    Future<Page<Workload>> findWorkloads(Pageable pageable);

    /**
     * Finds the workloads of one of the caller's organization's applications.
     *
     * @param applicationId an application of the caller's organization
     * @param pageable      the page of workloads to return
     * @return a future emitting the page of workloads, empty when the application runs none
     */
    Future<Page<Workload>> findWorkloadsForApplication(String applicationId, Pageable pageable);

    /**
     * Finds a single workload of the caller's organization.
     *
     * @param workloadId the id of the workload
     * @return a future emitting the workload, or failing when the caller's organization has no
     *         workload with that id
     */
    Future<Workload> findWorkload(String workloadId);

}
