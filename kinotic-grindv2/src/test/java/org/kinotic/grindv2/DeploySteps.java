package org.kinotic.grindv2;

import org.kinotic.grindv2.api.annotations.Step;
import org.kinotic.grindv2.api.model.StoreType;

import java.util.concurrent.CompletableFuture;

/**
 * The steps-class happy path: constructor injection from the application context, parameter
 * injection from inputs and earlier steps, an awaited asynchronous return, and durable state.
 */
public class DeploySteps {

    private final GreetingService greetingService;
    private final StepProbe probe;

    public DeploySteps(GreetingService greetingService, StepProbe probe) {
        this.greetingService = greetingService;
        this.probe = probe;
        probe.instantiations.incrementAndGet();
    }

    @Step(order = 1, value = "Resolve target", store = StoreType.STATE)
    public TargetState resolveTarget(ProjectRef project) {
        probe.record("resolve:" + project.id());
        return new TargetState(project.id() + "-node");
    }

    @Step(order = 2, value = "Sync source")
    public CompletableFuture<SyncedMarker> syncSource(TargetState target) {
        probe.record("sync:" + target.node());
        return CompletableFuture.completedFuture(new SyncedMarker("sha1"));
    }

    @Step(order = 3, value = "Ensure runtime")
    public void ensureRuntime(TargetState target, SyncedMarker synced) {
        probe.record("ensure:" + greetingService.greet(target.node()) + ":" + synced.sha());
        if (probe.failNext.get()) {
            throw new IllegalStateException("ensure fails");
        }
    }

}
