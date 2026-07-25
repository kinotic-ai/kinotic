package org.kinotic.gateway.internal.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One MCP content item; the gateway serves text content.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpContent {

    private McpContentType type;

    private String text;
}
