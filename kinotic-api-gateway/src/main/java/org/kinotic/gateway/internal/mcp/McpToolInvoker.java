package org.kinotic.gateway.internal.mcp;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Dispatches an MCP {@code tools/call} through the existing RPC path: the tool resolves to its stored CRI and
 * function, the arguments object is forwarded verbatim with the named-arguments content type, and the single reply
 * becomes the tool result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.disableMcp", havingValue = "false", matchIfMissing = true)
public class McpToolInvoker {

    private static final long CALL_TIMEOUT_SECONDS = 30;

    private final ServiceDirectory serviceDirectory;
    private final EventBusService eventBusService;
    private final JsonMapper jsonMapper;

    /**
     * Invokes the named tool for the given participant and completes with the MCP {@code tools/call} result node.
     * Service failures (offline, invocation error, timeout) complete normally with an {@code isError} result; an
     * unknown tool completes exceptionally with {@link IllegalArgumentException}.
     * @param toolName the MCP tool name to invoke
     * @param arguments the MCP arguments object, forwarded verbatim to the service
     * @param participant the authenticated caller, propagated as the event sender
     * @return a future completing with the {@code tools/call} result node
     */
    public CompletableFuture<McpCallToolResult> invoke(String toolName, ObjectNode arguments, Participant participant) {
        McpCallerScope scope = McpCallerScope.from(participant);
        // resolution uses the caller-visible query, so zone visibility is enforced by the lookup itself
        return serviceDirectory.findMcpToolsByName(toolName, scope.organizationId(), scope.applicationId())
                               .thenCompose(matches -> {
                                   CompletableFuture<McpCallToolResult> ret;
                                   if (matches.isEmpty()) {
                                       ret = CompletableFuture.failedFuture(
                                               new IllegalArgumentException("Unknown tool: " + toolName));
                                   } else if (matches.size() > 1) {
                                       // minted names are unique system wide, so more than one match means the stored
                                       // directory data is corrupted — a server fault the handler logs and masks as
                                       // an internal error, never a winner picked or CRIs leaked to the caller
                                       ret = CompletableFuture.failedFuture(new IllegalStateException(
                                               "Tool name '" + toolName + "' resolved to multiple services: "
                                                       + matches.stream().map(McpToolDefinition::getCri).toList()));
                                   } else {
                                       ret = dispatch(matches.getFirst(), arguments, participant);
                                   }
                                   return ret;
                               });
    }

    private CompletableFuture<McpCallToolResult> dispatch(McpToolDefinition tool, ObjectNode arguments, Participant participant) {
        CRI serviceCri = CRI.create(tool.getCri());
        CRI requestCri = CRI.create(serviceCri.scheme(),
                                    serviceCri.scope(),
                                    (serviceCri.hasZone() ? serviceCri.zone() + "~" : "") + serviceCri.resourceName(),
                                    "/" + tool.getFunctionName(),
                                    serviceCri.version());

        // A dedicated reply consumer per call: the reply CRI is unguessable and disposed after the single reply
        CRI replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                  UUID.randomUUID().toString(),
                                  "org.kinotic.gateway.McpToolInvoker");

        CompletableFuture<McpCallToolResult> ret = new CompletableFuture<>();
        EventConsumer replyConsumer = eventBusService.listen(replyCri);
        replyConsumer.handler(replyEvent -> {
            if (replyEvent.metadata().contains(EventConstants.ERROR_HEADER)) {
                ret.complete(toolError(replyEvent.metadata().get(EventConstants.ERROR_HEADER)));
            } else {
                byte[] data = replyEvent.data();
                ret.complete(toolResult(data != null ? new String(data, StandardCharsets.UTF_8) : "null"));
            }
        });

        replyConsumer.completion()
                     .onSuccess(v -> {
                         Metadata metadata = Metadata.create();
                         metadata.put(EventConstants.REPLY_TO_HEADER, replyCri.raw());
                         metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_NAMED_JSON);
                         Event<byte[]> event = Event.create(requestCri,
                                                            metadata,
                                                            jsonMapper.writeValueAsBytes(arguments),
                                                            participant);
                         eventBusService.sendWithAck(event)
                                        .onFailure(throwable -> {
                                            if (throwable instanceof ReplyException replyException
                                                    && replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                                                // fire-and-forget: reportUnreachable debounces and only writes verified state
                                                serviceDirectory.reportUnreachable(tool.getCri())
                                                                .exceptionally(reportFailure -> {
                                                                    log.debug("Failed to report unreachable service {}", tool.getCri(), reportFailure);
                                                                    return null;
                                                                });
                                                ret.complete(toolError("Service is offline: " + tool.getCri()));
                                            } else {
                                                ret.complete(toolError(throwable.getMessage()));
                                            }
                                        });
                     })
                     .onFailure(ret::completeExceptionally);

        return ret.orTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                  .exceptionally(throwable -> {
                      if (throwable instanceof TimeoutException) {
                          return toolError("The tool call timed out after " + CALL_TIMEOUT_SECONDS + " seconds");
                      }
                      throw throwable instanceof RuntimeException runtimeException
                              ? runtimeException
                              : new IllegalStateException(throwable);
                  })
                  .whenComplete((result, throwable) -> replyConsumer.unregister());
    }

    private McpCallToolResult toolResult(String text) {
        return McpCallToolResult.text(text, false);
    }

    private McpCallToolResult toolError(String message) {
        return McpCallToolResult.text(message, true);
    }
}
