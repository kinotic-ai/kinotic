package org.kinotic.system.internal.api.services.workload;

import lombok.RequiredArgsConstructor;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The {@link WorkloadRetentionSweeper}'s sweep as a system job. It runs hourly, and an operator can
 * run it ahead of the schedule. One sweep runs at a time across the cluster: a run started while
 * another is going is refused.
 */
@Component
@RequiredArgsConstructor
public class WorkloadRetentionJob {

    private final WorkloadRetentionSweeper sweeper;

    @Bean
    public JobDefinition workloadRetentionSweep() {
        return JobDefinition.create("Deletes the records and logs of workload runs that ended more than "
                                            + sweeper.getRetentionDays() + " days ago")
                            .name("workload-retention-sweep")
                            .version("2")
                            .every(Duration.ofHours(1))
                            .task(Tasks.fromCallable("Delete expired workloads", sweeper::sweep));
    }
}
