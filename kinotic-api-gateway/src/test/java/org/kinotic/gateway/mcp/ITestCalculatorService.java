package org.kinotic.gateway.mcp;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * An os-api-zone service exposing MCP tools, visible to organization and system participants.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.OS_API_ZONE)
public interface ITestCalculatorService {

    @McpTool(title = "Add Numbers", description = "Adds two numbers", readOnlyHint = true, idempotentHint = true)
    CompletableFuture<Integer> add(int left, int right);

    @McpTool(description = "Joins two strings with a space")
    CompletableFuture<String> join(String first, String second);
}
