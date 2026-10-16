package org.kinotic.system.internal.api.services.workload;

import lombok.RequiredArgsConstructor;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.api.services.SystemJob;
import org.springframework.stereotype.Component;

/**
 * The {@link WorkloadRetentionSweeper}'s sweep as a system job, so an operator can run it ahead of
 * the hourly schedule.
 */
@Component
@RequiredArgsConstructor
public class WorkloadRetentionJob implements SystemJob {

    private final WorkloadRetentionSweeper sweeper;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Deletes the records and logs of workload runs that ended more than "
                                            + sweeper.getRetentionDays() + " days ago")
                            .name("workload-retention-sweep")
                            .version("1")
                            .task(Tasks.fromCallable("Delete expired workloads",
                                                     sweeper::sweep));
    }
}
