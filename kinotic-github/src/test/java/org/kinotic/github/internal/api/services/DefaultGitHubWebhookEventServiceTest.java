package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.services.ProjectPushListener;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.internal.api.repositories.GitHubAppInstallationRepository;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultGitHubWebhookEventServiceTest {

    private static final String REPO_FULL_NAME = "acme/widgets";
    private static final String DEFAULT_BRANCH = "main";
    private static final String COMMIT_SHA = "a".repeat(40);
    private static final String ZERO_SHA = "0".repeat(40);

    private final List<ProjectPushListener> listeners = new ArrayList<>();
    private final RecordingPushListener recordingListener = new RecordingPushListener();

    private Vertx vertx;
    private ProjectRepository projectRepository;
    private DefaultGitHubWebhookEventService service;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        projectRepository = mock(ProjectRepository.class);
        listeners.add(recordingListener);
        service = new DefaultGitHubWebhookEventService(mock(GitHubAppInstallationRepository.class),
                                                       projectRepository,
                                                       providerOf(listeners),
                                                       vertx);
        service.init();
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    @Test
    void defaultBranchPushNotifiesListeners() {
        expectProject(projectFor(REPO_FULL_NAME));

        Future<Void> result = service.process(pushEvent("refs/heads/" + DEFAULT_BRANCH, COMMIT_SHA));

        assertTrue(result.succeeded());
        assertEquals(1, recordingListener.pushes.size());
        assertEquals("project-1", recordingListener.pushes.getFirst().project().getId());
        assertEquals(COMMIT_SHA, recordingListener.pushes.getFirst().commitSha());
    }

    @Test
    void nonDefaultBranchPushIsIgnored() {
        expectProject(projectFor(REPO_FULL_NAME));

        Future<Void> result = service.process(pushEvent("refs/heads/feature-x", COMMIT_SHA));

        assertTrue(result.succeeded());
        assertTrue(recordingListener.pushes.isEmpty());
    }

    @Test
    void branchDeletionPushIsIgnored() {
        expectProject(projectFor(REPO_FULL_NAME));

        GitHubWebhookEvent event = pushEvent("refs/heads/" + DEFAULT_BRANCH, ZERO_SHA);
        event.getPayload().put("deleted", true);
        Future<Void> result = service.process(event);

        assertTrue(result.succeeded());
        assertTrue(recordingListener.pushes.isEmpty());
    }

    @Test
    void nonPushRepoEventIsIgnored() {
        expectProject(projectFor(REPO_FULL_NAME));

        GitHubWebhookEvent event = pushEvent("refs/heads/" + DEFAULT_BRANCH, COMMIT_SHA)
                .setEventType("pull_request");
        Future<Void> result = service.process(event);

        assertTrue(result.succeeded());
        assertTrue(recordingListener.pushes.isEmpty());
    }

    @Test
    void pushWithoutKnownDefaultBranchIsIgnored() {
        Project project = projectFor(REPO_FULL_NAME);
        project.setRepoDefaultBranch(null);
        expectProject(project);

        Future<Void> result = service.process(pushEvent("refs/heads/" + DEFAULT_BRANCH, COMMIT_SHA));

        assertTrue(result.succeeded());
        assertTrue(recordingListener.pushes.isEmpty());
    }

    @Test
    void failingListenerDoesNotFailDeliveryOrStarveOthers() {
        expectProject(projectFor(REPO_FULL_NAME));
        listeners.addFirst((project, commitSha) -> Future.failedFuture(new RuntimeException("listener broke")));

        Future<Void> result = service.process(pushEvent("refs/heads/" + DEFAULT_BRANCH, COMMIT_SHA));

        assertTrue(result.succeeded());
        assertEquals(1, recordingListener.pushes.size());
    }

    private void expectProject(Project project) {
        when(projectRepository.findByRepoFullName(REPO_FULL_NAME))
                .thenReturn(Future.succeededFuture(List.of(project)));
    }

    private static Project projectFor(String repoFullName) {
        Project project = new Project();
        project.setId("project-1");
        project.setOrganizationId("org-1");
        project.setApplicationId("app-1");
        project.setRepoFullName(repoFullName);
        project.setRepoDefaultBranch(DEFAULT_BRANCH);
        return project;
    }

    private static GitHubWebhookEvent pushEvent(String ref, String afterSha) {
        return new GitHubWebhookEvent()
                .setEventType("push")
                .setDeliveryId("delivery-1")
                .setRepoFullName(REPO_FULL_NAME)
                .setPayload(new JsonObject().put("ref", ref).put("after", afterSha));
    }

    private static ObjectProvider<ProjectPushListener> providerOf(List<ProjectPushListener> listeners) {
        return new ObjectProvider<>() {
            @Override
            public ProjectPushListener getObject() {
                throw new UnsupportedOperationException("collection access only");
            }

            @Override
            public Stream<ProjectPushListener> stream() {
                return listeners.stream();
            }

            @Override
            public Iterator<ProjectPushListener> iterator() {
                return listeners.iterator();
            }
        };
    }

    private record PushNotification(Project project, String commitSha) {}

    private static class RecordingPushListener implements ProjectPushListener {

        private final List<PushNotification> pushes = new ArrayList<>();

        @Override
        public Future<Void> onPush(Project project, String commitSha) {
            pushes.add(new PushNotification(project, commitSha));
            return Future.succeededFuture();
        }
    }
}
