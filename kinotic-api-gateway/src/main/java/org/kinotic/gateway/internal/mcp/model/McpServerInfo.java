package org.kinotic.gateway.internal.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The serverInfo member of an MCP initialize result.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpServerInfo {

    private String name;

    private String version;
}
