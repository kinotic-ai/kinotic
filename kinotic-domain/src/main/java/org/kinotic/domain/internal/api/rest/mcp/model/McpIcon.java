package org.kinotic.domain.internal.api.rest.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * An optionally-sized icon a client can display for the server, per the MCP {@code Icon} type.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpIcon {

    private String src;

    private String mimeType;

    private List<String> sizes;
}
