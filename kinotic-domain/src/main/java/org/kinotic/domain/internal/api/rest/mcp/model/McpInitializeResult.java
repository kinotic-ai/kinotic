package org.kinotic.domain.internal.api.rest.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * The MCP initialize result: the negotiated protocol version, the server's capabilities, and its identity.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpInitializeResult {

    private String protocolVersion;

    private Map<String, Object> capabilities;

    private McpServerInfo serverInfo;
}
