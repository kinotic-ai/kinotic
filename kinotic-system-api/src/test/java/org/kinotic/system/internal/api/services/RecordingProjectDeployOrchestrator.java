package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.ArrayList;
import java.util.List;

/**
 * Records deploy requests and lets the test settle each one, so the event filtering and
 * per-project serialization can be observed without running real jobs.
 */
public class RecordingProjectDeployOrchestrator extends ProjectDeployOrchestrator {

    public final List<String> deployedShas = new ArrayList<>();
    public final List<Promise<Void>> outcomes = new ArrayList<>();

    public RecordingProjectDeployOrchestrator() {
        super(null, null, null, null);
    }

    @Override
    public Future<Void> deployProject(String organizationId, String projectId, String commitSha) {
        deployedShas.add(commitSha);
        Promise<Void> outcome = Promise.promise();
        outcomes.add(outcome);
        return outcome.future();
    }
}
