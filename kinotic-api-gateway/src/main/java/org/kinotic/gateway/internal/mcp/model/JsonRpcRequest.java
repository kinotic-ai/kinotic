package org.kinotic.gateway.internal.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.node.ObjectNode;

/**
 * A single JSON-RPC 2.0 request.
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JsonRpcRequest {

    private String jsonrpc;

    /**
     * The id keeps its wire value (string, number, or null) so responses echo it verbatim.
     */
    private Object id;

    private String method;

    private ObjectNode params;
}
