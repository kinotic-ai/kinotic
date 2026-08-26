package org.kinotic.system.internal.api.deployment;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.os.api.model.GitHubProjectEvent;
import org.kinotic.os.api.model.GitHubWebhookEvent;
import org.kinotic.os.api.services.GitHubProjectEventService;
import reactor.core.publisher.Sinks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Listener behavior over a scripted event stream: only pushes to the default branch
 * deploy, and per-project serialization collapses queued pushes to the newest commit.
 */
public class ProjectDeploymentListenerTest {

    private static final String SHA_1 = "1".repeat(40);
    private static final String SHA_2 = "2".repeat(40);
    private static final String SHA_3 = "3".repeat(40);

    private Sinks.Many<GitHubProjectEvent> events;
    private RecordingProjectDeployService deploys;
    private ProjectDeploymentListener listener;

    @BeforeEach
    void setUp() {
        events = Sinks.many().multicast().onBackpressureBuffer();
        deploys = new RecordingProjectDeployService();
        GitHubProjectEventService eventService = events::asFlux;
        listener = new ProjectDeploymentListener(eventService, deploys);
        listener.start();
    }

    @AfterEach
    void tearDown() {
        listener.stop();
    }

    @Test
    public void onlyPushesToTheDefaultBranchDeploy() {
        events.tryEmitNext(event("proj-1", push(SHA_1, "refs/heads/feature", "main", false)));
        events.tryEmitNext(event("proj-1", webhook("pull_request", new JsonObject())));
        events.tryEmitNext(event("proj-1", push("0".repeat(40), "refs/heads/main", "main", false)));
        events.tryEmitNext(event("proj-1", push(SHA_1, "refs/heads/main", "main", true)));
        events.tryEmitNext(event("proj-1", push(SHA_2, "refs/heads/main", "main", false)));

        assertEquals(List.of(SHA_2), deploys.deployedShas);
    }

    @Test
    public void pushesDuringADeploymentCollapseToTheNewestCommit() {
        events.tryEmitNext(event("proj-1", push(SHA_1, "refs/heads/main", "main", false)));
        events.tryEmitNext(event("proj-1", push(SHA_2, "refs/heads/main", "main", false)));
        events.tryEmitNext(event("proj-1", push(SHA_3, "refs/heads/main", "main", false)));
        assertEquals(List.of(SHA_1), deploys.deployedShas);

        // Finishing the first run deploys only the newest queued commit; SHA_2 is skipped
        deploys.outcomes.get(0).complete();
        assertEquals(List.of(SHA_1, SHA_3), deploys.deployedShas);

        // With nothing queued, completing the last run leaves the project idle
        deploys.outcomes.get(1).complete();
        assertEquals(2, deploys.deployedShas.size());

        events.tryEmitNext(event("proj-1", push(SHA_2, "refs/heads/main", "main", false)));
        assertEquals(List.of(SHA_1, SHA_3, SHA_2), deploys.deployedShas);
    }

    private static GitHubProjectEvent event(String projectId, GitHubWebhookEvent webhook) {
        return new GitHubProjectEvent("org-1", projectId, webhook);
    }

    private static GitHubWebhookEvent push(String after, String ref, String defaultBranch, boolean deleted) {
        JsonObject payload = new JsonObject()
                .put("ref", ref)
                .put("after", after)
                .put("deleted", deleted)
                .put("repository", new JsonObject().put("default_branch", defaultBranch));
        return webhook("push", payload);
    }

    private static GitHubWebhookEvent webhook(String eventType, JsonObject payload) {
        return new GitHubWebhookEvent(eventType, "delivery-1", 1L, "acme/proj", payload);
    }

}
