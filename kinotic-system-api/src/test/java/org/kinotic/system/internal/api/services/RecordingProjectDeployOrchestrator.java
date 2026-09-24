package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;

/**
 * Records deploy requests, so the event filtering can be observed without a repository.
 */
public class RecordingProjectDeployOrchestrator extends ProjectDeployOrchestrator {

    public final List<String> deployedShas = new ArrayList<>();

    public RecordingProjectDeployOrchestrator() {
        super(null, null, null, null, null, null, null, null, null);
    }

    @Override
    public Future<Void> deployProject(String organizationId, String projectId, String commitSha) {
        deployedShas.add(commitSha);
        return Future.succeededFuture();
    }
}
