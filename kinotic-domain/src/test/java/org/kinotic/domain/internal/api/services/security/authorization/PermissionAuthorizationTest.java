package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.*;
import org.junit.jupiter.api.*;
import org.kinotic.domain.api.config.OpenFgaProperties;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.internal.api.repositories.AuthorizationModelRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PermissionAuthorizationTest {
    private Vertx vertx;
    private AuthorizationModelRepository models;
    private OpenFgaClient client;
    private DefaultPermissionAuthorizationService service;
    private final AuthorizationScope scope = new AuthorizationScope().setKind(AuthorizationScopeKind.ORGANIZATION).setOrganizationId("acme");
    private final DefaultOrganizationParticipant actor = new DefaultOrganizationParticipant("alice", "acme", Map.of(), List.of());
    @BeforeEach void setup() {
        vertx = Vertx.vertx(); models = mock(AuthorizationModelRepository.class); client = mock(OpenFgaClient.class);
        service = new DefaultPermissionAuthorizationService(models, client, properties(Duration.ofSeconds(1)), vertx);
    }
    @AfterEach void close() throws Exception { vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS); }
    @Test void backendFailureCannotTurnIntoAnAllow() {
        when(models.findById(anyString())).thenReturn(Future.failedFuture("database unavailable"));
        assertThrows(Exception.class, () -> await(service.require(actor, scope, "project", "one", "projects.update")));
        verifyNoInteractions(client);
    }
    @Test void wrongOrganizationNeverCallsOpenFga() {
        var foreign = new AuthorizationScope().setKind(AuthorizationScopeKind.ORGANIZATION).setOrganizationId("other");
        assertThrows(Exception.class, () -> await(service.require(actor, foreign, "project", "one", "projects.update")));
        verifyNoInteractions(models, client);
    }
    @Test void pendingPublicationBlocksChecks() {
        when(models.findById(anyString())).thenReturn(Future.succeededFuture(model().setPending(true)));
        assertThrows(Exception.class, () -> await(service.require(actor, scope, "project", "one", "projects.update")));
        verifyNoInteractions(client);
    }
    @Test void changedRevisionRejectsAnOtherwiseSuccessfulCheck() {
        when(models.findById(anyString())).thenReturn(Future.succeededFuture(model()), Future.succeededFuture(model().setRevision(2)));
        when(client.check(anyString(), anyString(), any(), anyList())).thenReturn(Future.succeededFuture(true));
        assertThrows(Exception.class, () -> await(service.require(actor, scope, "project", "one", "projects.update")));
    }
    @Test void lateCompletionCannotRecoverFromTimeout() throws Exception {
        var properties = properties(Duration.ofMillis(20));
        var limited = new DefaultPermissionAuthorizationService(models, client, properties, vertx);
        Promise<Void> backend = Promise.promise();
        var decision = limited.bounded(backend::future);
        assertThrows(Exception.class, () -> await(decision));
        backend.complete();
        assertTrue(decision.failed());
    }
    private OpenFgaProperties properties(Duration timeout) { var properties = new OpenFgaProperties(); properties.setCheckTimeout(timeout); return properties; }
    private AuthorizationModel model() { return new AuthorizationModel().setId(scope.key()).setStoreId("store").setModelId("model").setRevision(1); }
    private <T> T await(Future<T> result) throws Exception { return result.toCompletionStage().toCompletableFuture().get(3, TimeUnit.SECONDS); }
}
