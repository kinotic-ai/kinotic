package org.kinotic.core.api.directory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A ready-to-serve MCP tool: the JSON Schema and sanitized tool name are built once when the service is published to the directory and stored, so
 * serving a tool listing or dispatching a call is a pure lookup. Resolution back to the invocable function uses the
 * stored {@link #cri}; the {@link #toolName} is never parsed apart.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolDefinition {

    /**
     * The MCP tool name, unique system wide: derived from the service's qualified name and the function, so a
     * customer service's tool name always carries its organization and application ids via the zone.
     */
    private String toolName;

    /**
     * The human-readable display title, or null when the tool has none.
     */
    private String title;

    /**
     * The LLM-facing description of what the tool does.
     */
    private String description;

    /**
     * The tool's input schema as a JSON Schema string.
     */
    private String inputSchema;

    /**
     * The full CRI of the tool's function, sent as-is to dispatch a call.
     */
    private String cri;

    private boolean readOnlyHint;

    private boolean destructiveHint;

    private boolean idempotentHint;

}
