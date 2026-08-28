package org.kinotic.management.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import reactor.core.publisher.Flux;

/**
 * Live view of grind job runs executing on one node. A run's event stream exists only in the
 * process executing it, so every node publishes its own instance of this service, scoped by
 * {@link #nodeId()} - a watch request is routed to the node recorded on the
 * {@link JobRun#getNodeId()} of the run being watched.
 */
@Publish
public interface JobWatchService {

    /**
     * The id of the node this instance serves, identifying it as the service's {@link Scope}.
     * @return the node id
     */
    @Scope
    String nodeId();

    /**
     * Opens a live view of a job run the participant may view, replaying every
     * {@link JobRunEvent} emitted since the run started and continuing until the run
     * terminates. A {@link TaskCompletedEvent} carries only its {@code wireValue} - the live
     * {@code storedValue} stays in the executing process.
     *
     * @param jobRunId the id of the run to watch
     * @return the run's event stream, empty when the run is not currently executing on this
     *         node
     */
    Flux<JobRunEvent> watch(String jobRunId);

}
