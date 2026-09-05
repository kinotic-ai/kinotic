package org.kinotic.domain.internal.api.rest.mcp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The serverInfo member of an MCP initialize result.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpServerInfo {

    private String name;

    private String title;

    private String version;

    private String websiteUrl;

    private List<McpIcon> icons;
}
