package org.kinotic.gateway.internal.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A JSON-RPC 2.0 response: the echoed request id plus exactly one of {@code result} or {@code error}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JsonRpcResponse {

    private final String jsonrpc = "2.0";

    // no inclusion filter: the spec requires "id": null on responses to unidentifiable requests
    private Object id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object result;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonRpcError error;

    public static JsonRpcResponse result(Object id, Object result) {
        return new JsonRpcResponse().setId(id).setResult(result);
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        return new JsonRpcResponse().setId(id)
                                    .setError(new JsonRpcError().setCode(code).setMessage(message));
    }
}
