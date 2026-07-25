package org.kinotic.core.api.directory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.node.ObjectNode;

/**
 * A ready-to-serve MCP tool, built once when the service is published to the directory and stored, so it is
 * served in a {@code tools/list} verbatim and dispatching a call is a pure lookup. The {@link #name} is never
 * parsed apart.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolDefinition {

    /**
     * The MCP tool name, {@code <serviceName>.<functionName>}. Resolution is always scoped to a caller's
     * organization/application, so a name only has to be unique among the tools that scope can see; an
     * ambiguous name within one scope fails resolution rather than picking a winner.
     */
    private String name;

    /**
     * The human-readable display title, or null when the tool has none.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String title;

    /**
     * The LLM-facing description of what the tool does.
     */
    private String description;

    /**
     * The tool's input schema as a JSON Schema object.
     */
    private ObjectNode inputSchema;

    /**
     * The full CRI of the tool's function, sent as-is to dispatch a call. Null when the tool was loaded for a
     * listing, which never exposes the CRI to callers.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String cri;

    /**
     * The behavior hints served as the MCP tool annotations.
     */
    private McpToolAnnotations annotations;

}
