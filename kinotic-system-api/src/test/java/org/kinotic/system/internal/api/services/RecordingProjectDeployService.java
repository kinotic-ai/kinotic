package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.management.api.services.github.GitHubProjectEventService;

import java.util.ArrayList;
import java.util.List;

/**
 * Records deploy requests and lets the test settle each one, so the event filtering and
 * per-project serialization can be observed without running real jobs.
 */
public class RecordingProjectDeployService extends ProjectDeployService {

    public final List<String> deployedShas = new ArrayList<>();
    public final List<Promise<Void>> outcomes = new ArrayList<>();

    public RecordingProjectDeployService(GitHubProjectEventService eventService) {
        super(eventService, null, null, null, null);
    }

    @Override
    public Future<Void> deployProject(String organizationId, String projectId, String commitSha) {
        deployedShas.add(commitSha);
        Promise<Void> outcome = Promise.promise();
        outcomes.add(outcome);
        return outcome.future();
    }
}
