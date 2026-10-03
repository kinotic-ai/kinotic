package org.kinotic.test.tests.core.authz;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Verifies the platform store runs the authorization model generated from the service directory once the
 * full stack boots: the directory publishes its entries, the model is generated from every contract and
 * written to the engine. The store's current model is read back through the engine's own API, so the
 * assertion sees what the servers' checks will see.
 */
@SpringBootTest
public class PlatformModelSyncTests extends KinoticTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private KinoticAuthzProperties authzProperties;

    @Test
    public void platformStoreRunsTheModelGeneratedFromTheDirectory() throws Exception {
        List<ServiceDirectoryEntry> entries = serviceDirectory.findEntriesScopedTo(null, null, Pageable.create(0, 500, Sort.by("id")))
                                                              .await()
                                                              .getContent();
        Assertions.assertFalse(entries.isEmpty(), "the directory holds the published contracts");
        AuthzModel expected = modelGenerator.platformModel(entries.stream().map(ServiceDirectoryEntry::getServiceDefinition).toList());
        String storeId = storeService.platformStoreId().await();

        // the model is written after the directory publishes on ApplicationReadyEvent, so poll for it
        Map<String, TreeSet<String>> written = Map.of();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !written.equals(relationsByType(expected.definition()))) {
            JsonNode latest = latestModel(storeId);
            written = latest == null ? Map.of() : relationsByType(latest);
            if (!written.equals(relationsByType(expected.definition()))) {
                Thread.sleep(1000);
            }
        }

        Assertions.assertEquals(relationsByType(expected.definition()), written,
                                "the platform store's current model has the generated model's types and relations");
    }

    /**
     * The store's newest model version as the engine returns it, or null while it has none.
     */
    private JsonNode latestModel(String storeId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authzProperties.getAuthz().getApiUrl()
                                                                        + "/stores/" + storeId + "/authorization-models?page_size=1"))
                                         .GET()
                                         .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), response.body());
        JsonNode models = MAPPER.readTree(response.body()).path("authorization_models");
        return models.isEmpty() ? null : models.get(0);
    }

    private static Map<String, TreeSet<String>> relationsByType(JsonNode definition) {
        Map<String, TreeSet<String>> ret = new TreeMap<>();
        for (JsonNode type : definition.path("type_definitions")) {
            ret.put(type.path("type").asString(), new TreeSet<>(type.path("relations").propertyNames()));
        }
        return ret;
    }

}
