package org.kinotic.domain.internal.api.rest.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The error member of a JSON-RPC 2.0 response.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JsonRpcError {

    private int code;

    private String message;
}
