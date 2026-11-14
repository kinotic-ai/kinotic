package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.system.api.model.SystemJobDescriptor;

import java.util.List;

/**
 * Starts the system jobs on demand. Published in the system zone, which only SYSTEM participants
 * may address. A run is owned by the system and executes on the node that started it,
 * and is listed, watched and read through {@code JobMonitoringService} like any other job run.
 */
@Publish
@AuthzResource(value = AuthzConstants.SYSTEM_TYPE, resourceId = AuthzConstants.SYSTEM_OBJECT_ID, permission = AuthzConstants.CAN_MANAGE_CLUSTER)
public interface SystemJobService {

    /**
     * Lists the system jobs that can be started.
     *
     * @return the jobs, ordered by name
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_JOBS)
    Future<List<SystemJobDescriptor>> findSystemJobs();

    /**
     * Starts a run of the named system job on behalf of the system.
     *
     * @param name the name of the job to run
     * @return a future completing with the started run once it and its task ledger are recorded, or
     *         failing when no system job has the given name
     */
    Future<JobRun> startSystemJob(String name);

}
