package org.kinotic.system.internal.api.services.workload;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.TaskLogger;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.api.services.SystemJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * The {@link WorkloadRetentionSweeper}'s sweep as a system job, so an operator can run it ahead of
 * the hourly schedule and read what it deleted.
 */
@Component
@RequiredArgsConstructor
public class WorkloadRetentionJob implements SystemJob {

    private final WorkloadRetentionSweeper sweeper;

    @Override
    public JobDefinition createJobDefinition() {
        int retentionDays = sweeper.getRetentionDays();
        return JobDefinition.create("Deletes the records and logs of workload runs that ended more than "
                                            + retentionDays + " days ago")
                            .name("workload-retention-sweep")
                            .version("1")
                            .task(Tasks.fromCallable("Delete expired workloads", new Callable<Future<Integer>>() {

                                @Autowired
                                private TaskLogger logger;

                                @Override
                                public Future<Integer> call() {
                                    return sweeper.sweep(deleted -> logger.info("Deleted {} workloads", deleted))
                                                  .onSuccess(total -> logger.info("Deleted {} workloads in all whose runs ended more than {} days ago",
                                                                                  total, retentionDays));
                                }
                            }));
    }
}
