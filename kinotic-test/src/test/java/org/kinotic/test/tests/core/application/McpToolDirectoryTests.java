package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Verifies the MCP-exposed platform services land in the service directory when the full stack boots: schema
 * generation, tool minting, the Elasticsearch upsert, and the liveness marking all have to succeed for a tool
 * to appear in a listing.
 */
@SpringBootTest
public class McpToolDirectoryTests extends KinoticTestBase {

    private static final String PROJECT_SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String APPLICATION_SERVICE = "management-api~org.kinotic.management.api.services.ApplicationService";

    private static final String FIND_PROJECTS_BY_REPO = KinoticUtil.mcpToolName(PROJECT_SERVICE, "findByRepoFullName");
    private static final String GET_OIDC_CONFIGURATIONS = KinoticUtil.mcpToolName(APPLICATION_SERVICE, "getOidcConfigurations");

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Test
    public void mcpExposedServicesArePublishedAndListed() throws Exception {
        // the directory publishes on ApplicationReadyEvent and liveness flips entries online one at a
        // time, so the poll must wait for every expected tool before asserting anything
        List<String> expected = List.of(FIND_PROJECTS_BY_REPO, GET_OIDC_CONFIGURATIONS);
        CursorPageable pageable = Pageable.create(null, 1000, Sort.by("id"));
        List<String> names = List.of();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !names.containsAll(expected)) {
            names = serviceDirectory.findMcpToolsCallableBy(null, null, pageable)
                                    .await()
                                    .getTools()
                                    .stream()
                                    .map(McpToolDefinition::getName)
                                    .toList();
            if (!names.containsAll(expected)) {
                Thread.sleep(1000);
            }
        }

        Assertions.assertTrue(names.containsAll(expected),
                              "timed out waiting for " + expected + " in the directory listing, got: " + names);
    }

}
