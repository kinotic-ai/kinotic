package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.*;
import org.junit.jupiter.api.*;
import org.kinotic.core.api.directory.*;
import org.kinotic.core.api.event.*;
import org.kinotic.domain.api.config.OpenFgaProperties;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.internal.api.repositories.AuthorizationModelRepository;
import org.kinotic.domain.internal.model.security.AuthorizationTuple;
import org.kinotic.idl.api.schema.*;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpenFgaRequestAuthorizerTest {
    private Vertx vertx;
    private OpenFgaClient client;
    private OpenFgaRequestAuthorizer authorizer;
    private final DefaultApplicationParticipant actor = new DefaultApplicationParticipant("alice", "acme", "billing", "tenant-a", Map.of(), List.of());
    @BeforeEach void setup() {
        vertx = Vertx.vertx();
        var models = mock(AuthorizationModelRepository.class);
        client = mock(OpenFgaClient.class);
        var model = new AuthorizationModel().setStoreId("store").setModelId("model").setRevision(1);
        when(models.findById(anyString())).thenReturn(Future.succeededFuture(model));
        when(client.check(anyString(), anyString(), any(), anyList())).thenReturn(Future.succeededFuture(true));
        var properties = new OpenFgaProperties(); properties.setCheckTimeout(Duration.ofSeconds(1));
        var authorization = new DefaultPermissionAuthorizationService(models, client, properties, vertx);
        var declaration = new RequirePermissionC3Decorator().setPermission("invoices.update").setResourceType("invoice").setIdArgument("invoiceId").setArgumentIndex(1);
        var function = new FunctionDefinition().setName("update").addParameter("note", new StringC3Type()).addParameter("invoiceId", new StringC3Type());
        function.setDecorators(List.of(declaration));
        var entry = new ServiceDirectoryEntry().setOrganizationId("acme").setApplicationId("billing").setVersion("1.0.0")
                .setServiceDefinition(new ServiceDefinition().addFunction(function));
        var directory = mock(ServiceDirectory.class);
        when(directory.findEntryById("app.acme.billing~billing.InvoiceService")).thenReturn(Future.succeededFuture(entry));
        authorizer = new OpenFgaRequestAuthorizer(JsonMapper.builder().build(), directory, authorization, List.of());
    }
    @AfterEach void close() throws Exception { vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS); }
    @Test void unversionedRequestAndLeadingSlashUseTheExplicitSecondArgument() throws Exception {
        await(authorizer.authorize(event("application/json", "[\"wrong-id\",\"invoice-one\"]", "")));
        var tuple = ArgumentCaptor.forClass(AuthorizationTuple.class);
        verify(client).check(eq("store"), eq("model"), tuple.capture(), anyList());
        assertEquals(AuthorizationCompiler.resource(AuthorizationScope.from(actor), "invoice", "invoice-one", "invoices.update"), tuple.getValue().object());
    }
    @Test void namedArgumentsResolveByName() throws Exception {
        await(authorizer.authorize(event(EventConstants.CONTENT_TYPE_NAMED_JSON, "{\"invoiceId\":\"invoice-two\",\"note\":\"wrong-id\"}", "")));
        var tuple = ArgumentCaptor.forClass(AuthorizationTuple.class);
        verify(client).check(eq("store"), eq("model"), tuple.capture(), anyList());
        assertEquals(AuthorizationCompiler.resource(AuthorizationScope.from(actor), "invoice", "invoice-two", "invoices.update"), tuple.getValue().object());
    }
    @Test void wrongVersionNeverReachesOpenFga() {
        assertThrows(Exception.class, () -> await(authorizer.authorize(event("application/json", "[\"note\",\"id\"]", "#0.9.0"))));
        verifyNoInteractions(client);
    }
    @Test void missingOrNumericIdCannotBecomeABroadGrant() {
        assertThrows(Exception.class, () -> await(authorizer.authorize(event("application/json", "[\"note\",42]", ""))));
        verifyNoInteractions(client);
    }
    private Event<byte[]> event(String contentType, String body, String version) {
        Metadata metadata = Metadata.create(); metadata.put(EventConstants.CONTENT_TYPE_HEADER, contentType);
        return Event.create(CRI.create("srv://app.acme.billing~billing.InvoiceService/update" + version), metadata, body.getBytes(StandardCharsets.UTF_8), actor);
    }
    private <T> T await(Future<T> value) throws Exception { return value.toCompletionStage().toCompletableFuture().get(3, TimeUnit.SECONDS); }
}
