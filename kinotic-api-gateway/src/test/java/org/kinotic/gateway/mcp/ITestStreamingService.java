package org.kinotic.gateway.mcp;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.McpTool;
import reactor.core.publisher.Flux;

/**
 * Carries an {@code @McpTool} with a streaming return type, so publishing it to the directory must be rejected
 * and no entry stored.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.OS_API_ZONE)
public interface ITestStreamingService {

    @McpTool(description = "Streams ticks, which MCP tools do not support")
    Flux<String> ticker();
}
