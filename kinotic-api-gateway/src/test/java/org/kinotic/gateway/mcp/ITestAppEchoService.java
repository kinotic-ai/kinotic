package org.kinotic.gateway.mcp;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * An application-zone service exposing an MCP tool, visible only to its own application and system participants.
 */
@Publish
@Version("1.0.0")
@Zone("app." + McpE2eTests.TEST_ORG_ID + "." + McpE2eTests.TEST_APP_ID)
public interface ITestAppEchoService {

    @McpTool(description = "Echoes the given message")
    CompletableFuture<String> echo(String message);
}
