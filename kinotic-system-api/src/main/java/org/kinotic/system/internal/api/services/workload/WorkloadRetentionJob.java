package org.kinotic.system.internal.api.services.workload;

import lombok.RequiredArgsConstructor;
import org.apache.ignite.Ignite;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.internal.api.services.SystemJob;
import org.springframework.stereotype.Component;

/**
 * The {@link WorkloadRetentionSweeper}'s sweep as a system job, so a system user can run it ahead of
 * the hourly schedule. The run's sweep is the cluster singleton's, so it never races the hourly
 * sweep or another run's: one requested while a sweep is running joins it.
 */
@Component
@RequiredArgsConstructor
public class WorkloadRetentionJob implements SystemJob {

    private final WorkloadRetentionSweeper sweeper;
    private final Ignite ignite;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Deletes the records and logs of workload runs that ended more than "
                                            + sweeper.getRetentionDays() + " days ago")
                            .name("workload-retention-sweep")
                            .version("1")
                            .task(Tasks.fromCallable("Delete expired workloads", () -> {
                                WorkloadCleanupService.proxy(ignite).sweep();
                                return null;
                            }));
    }
}
