package org.kinotic.gateway.internal.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/**
 * A JSON-RPC 2.0 response: the echoed request id plus exactly one of {@code result} or {@code error}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JsonRpcResponse {

    private final String jsonrpc = "2.0";

    private JsonNode id = NullNode.getInstance();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object result;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonRpcError error;

    public static JsonRpcResponse result(JsonNode id, Object result) {
        return new JsonRpcResponse().setId(id == null ? NullNode.getInstance() : id).setResult(result);
    }

    public static JsonRpcResponse error(JsonNode id, int code, String message) {
        return new JsonRpcResponse().setId(id == null ? NullNode.getInstance() : id)
                                    .setError(new JsonRpcError().setCode(code).setMessage(message));
    }
}
