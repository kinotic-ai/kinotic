package org.kinotic.test.tests.core.application;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
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

    private static final String FIND_PROJECTS_BY_REPO =
            "os-api.org.kinotic.os.api.services.ProjectService.findByRepoFullName";
    private static final String GET_OIDC_CONFIGURATIONS =
            "os-api.org.kinotic.os.api.services.ApplicationService.getOidcConfigurations";

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Test
    public void mcpExposedServicesArePublishedAndListed() throws Exception {
        // the directory publishes on ApplicationReadyEvent and liveness flips entries online; poll until settled
        CursorPageable pageable = Pageable.create(null, 1000, Sort.by("id"));
        List<String> names = List.of();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !names.contains(FIND_PROJECTS_BY_REPO)) {
            names = serviceDirectory.findMcpToolsCallableBy(null, null, pageable)
                                    .await()
                                    .getTools()
                                    .stream()
                                    .map(McpToolDefinition::getName)
                                    .toList();
            if (!names.contains(FIND_PROJECTS_BY_REPO)) {
                Thread.sleep(1000);
            }
        }

        Assertions.assertTrue(names.contains(FIND_PROJECTS_BY_REPO),
                              "ProjectService tool missing from the directory listing, got: " + names);
        Assertions.assertTrue(names.contains(GET_OIDC_CONFIGURATIONS),
                              "ApplicationService tool missing from the directory listing, got: " + names);
    }

}
