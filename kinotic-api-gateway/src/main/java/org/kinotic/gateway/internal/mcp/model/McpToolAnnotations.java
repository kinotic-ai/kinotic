package org.kinotic.gateway.internal.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The MCP tool annotations served with a listing: behavior hints declared by the tool's {@code @McpTool}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolAnnotations {

    private boolean readOnlyHint;

    private boolean destructiveHint;

    private boolean idempotentHint;
}
