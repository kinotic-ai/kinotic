package org.kinotic.core.api.directory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The MCP tool annotations: behavior hints declared by the tool's {@code @McpTool}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolAnnotations {

    private boolean readOnlyHint;

    private boolean destructiveHint;

    private boolean idempotentHint;

    private boolean openWorldHint;
}
