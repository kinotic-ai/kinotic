package org.kinotic.gateway.internal.mcp;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.*;
import org.kinotic.core.api.security.Participant;
import org.kinotic.gateway.internal.mcp.model.McpCallToolResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches an MCP {@code tools/call} through the existing RPC path: the tool resolves to its stored CRI, the
 * arguments object is forwarded verbatim with the named-arguments content type, and the single reply becomes the
 * tool result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.disableMcp", havingValue = "false", matchIfMissing = true)
public class McpToolInvoker {

    private final ServiceDirectory serviceDirectory;
    private final EventBusService eventBusService;
    private final JsonMapper jsonMapper;

    // one reply consumer serves every call, with replies matched to callers by correlation id — the same
    // pattern DefaultRpcServiceProxyHandle uses, since registering a consumer cluster wide is slow
    private final CRI replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                            UUID.randomUUID().toString(),
                                            "org.kinotic.gateway.McpToolInvoker");
    private final ConcurrentHashMap<String, CompletableFuture<McpCallToolResult>> pendingCalls = new ConcurrentHashMap<>();
    private EventConsumer replyConsumer;
    // false until the cluster-wide registration completes, and left false forever if it fails,
    // which keeps tools/call disabled rather than dropping replies
    private volatile boolean ready = false;

    @PostConstruct
    void listenForReplies() {
        replyConsumer = eventBusService.listen(replyCri);
        replyConsumer.handler(replyEvent -> {
            String correlationId = replyEvent.metadata().get(EventConstants.CORRELATION_ID_HEADER);
            CompletableFuture<McpCallToolResult> pending = correlationId != null ? pendingCalls.remove(correlationId) : null;
            if (pending == null) {
                // a reply landing after its call timed out has no caller left to complete
                log.debug("Discarding MCP reply with correlation id {}", correlationId);
            } else if (replyEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                pending.complete(McpCallToolResult.text(replyEvent.metadata().get(EventConstants.ERROR_HEADER), true));
            } else {
                byte[] data = replyEvent.data();
                pending.complete(McpCallToolResult.text(data != null ? new String(data, StandardCharsets.UTF_8) : "null", false));
            }
        });
        replyConsumer.completion()
                     .onSuccess(v -> ready = true)
                     .onFailure(throwable -> log.error("MCP reply consumer registration failed, tools/call is disabled", throwable));
    }

    @PreDestroy
    void stopListening() {
        replyConsumer.unregister();
    }

    /**
     * Invokes the named tool for the given participant and completes with the MCP {@code tools/call} result node.
     * Service failures (offline, invocation error) complete normally with an {@code isError} result; an
     * unknown tool completes exceptionally with {@link IllegalArgumentException}.
     * @param toolName the MCP tool name to invoke
     * @param arguments the MCP arguments object, forwarded verbatim to the service
     * @param participant the authenticated caller, propagated as the event sender
     * @return a future completing with the {@code tools/call} result node
     */
    public CompletableFuture<McpCallToolResult> invoke(String toolName, ObjectNode arguments, Participant participant) {
        if (!ready) {
            return CompletableFuture.completedFuture(McpCallToolResult.text("The MCP endpoint is not ready", true));
        }
        McpCallerScope scope = McpCallerScope.from(participant);
        // resolution uses the caller-visible query, so zone visibility is enforced by the lookup itself
        return serviceDirectory.findMcpToolByName(toolName, scope.organizationId(), scope.applicationId())
                               .thenCompose(tool -> {
                                   CompletableFuture<McpCallToolResult> ret;
                                   if (tool == null) {
                                       ret = CompletableFuture.failedFuture(
                                               new IllegalArgumentException("Unknown tool: " + toolName));
                                   } else {
                                       ret = dispatch(tool, arguments, participant);
                                   }
                                   return ret;
                               });
    }

    private CompletableFuture<McpCallToolResult> dispatch(McpToolDefinition tool, ObjectNode arguments, Participant participant) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<McpCallToolResult> ret = new CompletableFuture<>();
        pendingCalls.put(correlationId, ret);

        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.REPLY_TO_HEADER, replyCri.raw());
        metadata.put(EventConstants.CORRELATION_ID_HEADER, correlationId);
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_NAMED_JSON);
        Event<byte[]> event = Event.create(CRI.create(tool.getCri()),
                                           metadata,
                                           jsonMapper.writeValueAsBytes(arguments),
                                           participant);
        eventBusService.sendWithAck(event)
                       .onFailure(throwable -> {
                           // a failed send never gets a reply, so its pending entry is removed here
                           pendingCalls.remove(correlationId);
                           if (throwable instanceof ReplyException replyException
                                   && replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                               // fire-and-forget: reportUnreachable debounces and only writes verified state
                               serviceDirectory.reportUnreachable(tool.getCri())
                                               .exceptionally(reportFailure -> {
                                                   log.debug("Failed to report unreachable service {}", tool.getCri(), reportFailure);
                                                   return null;
                                               });
                               ret.complete(McpCallToolResult.text("Service is offline: " + tool.getCri(), true));
                           } else {
                               ret.complete(McpCallToolResult.text(throwable.getMessage(), true));
                           }
                       });

        return ret;
    }

}
