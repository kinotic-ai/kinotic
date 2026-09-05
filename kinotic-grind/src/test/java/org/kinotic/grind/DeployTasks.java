package org.kinotic.grind;

import org.kinotic.grind.api.annotations.Task;
import org.kinotic.grind.api.model.StoreType;

import java.util.concurrent.CompletableFuture;

/**
 * The tasks-class happy path: constructor injection from the application context, parameter
 * injection from inputs and earlier tasks, an awaited asynchronous return, and durable state.
 */
public class DeployTasks {

    private final GreetingService greetingService;
    private final TaskProbe probe;

    public DeployTasks(GreetingService greetingService, TaskProbe probe) {
        this.greetingService = greetingService;
        this.probe = probe;
        probe.instantiations.incrementAndGet();
    }

    @Task(order = 1, value = "Resolve target", store = StoreType.STATE)
    public TargetState resolveTarget(ProjectRef project) {
        probe.record("resolve:" + project.id());
        return new TargetState(project.id() + "-node");
    }

    @Task(order = 2, value = "Sync source")
    public CompletableFuture<SyncedMarker> syncSource(TargetState target) {
        probe.record("sync:" + target.node());
        return CompletableFuture.completedFuture(new SyncedMarker("sha1"));
    }

    @Task(order = 3, value = "Ensure runtime")
    public void ensureRuntime(TargetState target, SyncedMarker synced) {
        probe.record("ensure:" + greetingService.greet(target.node()) + ":" + synced.sha());
        if (probe.failNext.get()) {
            throw new IllegalStateException("ensure fails");
        }
    }

}
