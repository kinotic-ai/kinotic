package org.kinotic.core.api.directory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A ready-to-serve MCP tool: the JSON Schema and sanitized tool name are built once when the service is published to the directory and stored, so
 * serving a tool listing or dispatching a call is a pure lookup. Resolution back to the invocable function uses the
 * stored {@link #cri} and {@link #functionName}; the {@link #toolName} is never parsed apart.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolDefinition {

    /**
     * The MCP tool name, sanitized to {@code ^[a-zA-Z0-9_-]{1,128}$}.
     */
    private String toolName;

    /**
     * The LLM-facing description of what the tool does.
     */
    private String description;

    /**
     * The tool's input schema as a JSON Schema string.
     */
    private String inputSchema;

    /**
     * The CRI of the service that provides the function, used to dispatch a call.
     */
    private String cri;

    /**
     * The name of the function to invoke on the service.
     */
    private String functionName;

    private boolean readOnlyHint;

    private boolean destructiveHint;

    private boolean idempotentHint;

}
