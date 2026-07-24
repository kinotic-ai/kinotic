package org.kinotic.gateway.internal.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A single JSON-RPC 2.0 request. The id keeps its wire type so responses echo it verbatim.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JsonRpcRequest {

    private String jsonrpc;

    private JsonNode id;

    private String method;

    private ObjectNode params;
}
