package org.kinotic.management.internal.api.rest.mcp;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.management.internal.api.rest.mcp.model.McpCallToolResult;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the authorization of a {@code tools/call}: the call is checked with the named arguments it then
 * dispatches with, a refusal is the tool's error result rather than a failed call, and a check that cannot be
 * made fails the call.
 */
class McpToolInvokerTests {

    private static final String TOOL_CRI = "srv://management-api~org.kinotic.management.api.services.ProjectService/save#1.0.0";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private EventBusService eventBusService;
    private RequestAuthorizer requestAuthorizer;
    private McpToolInvoker invoker;

    @BeforeEach
    void setUp() {
        eventBusService = mock(EventBusService.class);
        EventConsumer replyConsumer = mock(EventConsumer.class);
        when(replyConsumer.handler(any())).thenReturn(replyConsumer);
        when(replyConsumer.completion()).thenReturn(Future.succeededFuture());
        when(eventBusService.listen(any())).thenReturn(replyConsumer);
        when(eventBusService.sendWithAck(any())).thenReturn(Future.succeededFuture("node-1"));

        ServiceDirectory serviceDirectory = mock(ServiceDirectory.class);
        McpToolDefinition tool = new McpToolDefinition().setName("save-project").setCri(TOOL_CRI);
        when(serviceDirectory.findMcpToolByName(eq("save-project"), eq("acme"), any())).thenReturn(Future.succeededFuture(tool));

        requestAuthorizer = mock(RequestAuthorizer.class);
        invoker = new McpToolInvoker(eventBusService,
                                     jsonMapper,
                                     serviceDirectory,
                                     requestAuthorizer,
                                     mock(RequestLivenessWatcher.class),
                                     ZonePartitioningService.everyZone("test"));
        invoker.init();
    }

    @Test
    void anAdmittedCallIsDispatchedWithTheArgumentsItWasCheckedWith() throws Exception {
        when(requestAuthorizer.authorize(any(), any(), any())).thenReturn(Future.succeededFuture());
        ObjectNode arguments = jsonMapper.createObjectNode();
        arguments.putObject("entity").put("id", "proj-a");

        // the call completes with the service's reply, which never comes here; the dispatch is what is pinned
        invoker.invoke("save-project", arguments, sally());

        ArgumentCaptor<ObjectNode> checked = ArgumentCaptor.forClass(ObjectNode.class);
        verify(requestAuthorizer).authorize(eq(CRI.create(TOOL_CRI)), eq(sally()), checked.capture());
        ArgumentCaptor<Event<byte[]>> sent = ArgumentCaptor.forClass(Event.class);
        verify(eventBusService).sendWithAck(sent.capture());
        assertEquals(checked.getValue(), jsonMapper.readTree(sent.getValue().data()));
        assertEquals(EventConstants.CONTENT_TYPE_NAMED_JSON, sent.getValue().metadata().get(EventConstants.CONTENT_TYPE_HEADER));
    }

    @Test
    void aRefusedCallIsTheToolsErrorResult() throws Exception {
        when(requestAuthorizer.authorize(any(), any(), any()))
                .thenReturn(Future.failedFuture(new AuthorizationException("Not authorized")));

        McpCallToolResult result = invoker.invoke("save-project", jsonMapper.createObjectNode(), sally())
                                          .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(result.isError());
        verify(eventBusService, never()).sendWithAck(any());
    }

    @Test
    void aCheckThatCannotBeMadeFailsTheCall() {
        IllegalStateException down = new IllegalStateException("engine unreachable");
        when(requestAuthorizer.authorize(any(), any(), any())).thenReturn(Future.failedFuture(down));

        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> invoker.invoke("save-project", jsonMapper.createObjectNode(), sally())
                                                               .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertEquals(down, failure.getCause());
        assertFalse(failure.getCause() instanceof AuthorizationException);
        verify(eventBusService, never()).sendWithAck(any());
    }

    private static DefaultOrganizationParticipant sally() {
        return DefaultOrganizationParticipant.builder().id("sally").organizationId("acme").metadata(Map.of()).roles(List.of()).build();
    }
}
