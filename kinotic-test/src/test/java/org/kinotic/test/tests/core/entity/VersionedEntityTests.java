package org.kinotic.test.tests.core.entity;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.VersionDecorator;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins how a {@link VersionDecorator} field rides on Elasticsearch's optimistic concurrency, from the write
 * that sends it to the read that returns it: the version a write returns is the document's primary term and
 * sequence number, a write is checked against the version it carries, and the version is never part of the
 * stored document.
 */
@SpringBootTest
public class VersionedEntityTests extends KinoticTestBase {

    private static final String TENANT = "tenant1";
    private static final String VERSION_FIELD = "version";

    @Autowired
    private ElasticsearchAsyncClient esAsyncClient;
    @Autowired
    private EntitiesService entitiesService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataService testDataService;

    @Test
    public void saveCreatesWithoutAVersionAndReplacesWithOne() throws Exception {
        EntityDefinition definition = createVersionedPersonDefinition();
        EntityContext context = context();

        JsonNode created = await(save(definition, context, person("p1", "Ann", "Smith", null)));
        assertStoredVersion(definition, "p1", versionOf(created));

        Throwable duplicate = failure(save(definition, context, person("p1", "Ann", "Smith", null)));
        assertVersionConflict(duplicate, "A save without a version creates, so a second one for the same id conflicts");

        JsonNode replaced = await(save(definition, context, person("p1", "Ann", "Jones", versionOf(created))));
        assertNotEquals(versionOf(created), versionOf(replaced), "A successful write returns the document's new version");
        assertStoredVersion(definition, "p1", versionOf(replaced));
        assertEquals("Jones", findById(definition, context, "p1").get("lastName").asString());

        Throwable stale = failure(save(definition, context, person("p1", "Ann", "Brown", versionOf(created))));
        assertVersionConflict(stale, "A save carrying a superseded version is refused");
        assertEquals("Jones", findById(definition, context, "p1").get("lastName").asString(), "The refused write changed nothing");
    }

    @Test
    public void updateRequiresTheCurrentVersion() throws Exception {
        EntityDefinition definition = createVersionedPersonDefinition();
        EntityContext context = context();

        JsonNode created = await(save(definition, context, person("p1", "Ann", "Smith", null)));

        Throwable missing = failure(update(definition, context, Map.of("id", "p1", "lastName", "Jones")));
        assertInstanceOf(IllegalArgumentException.class, missing, "An update without a version is refused before it reaches Elasticsearch");

        JsonNode updated = await(update(definition, context, Map.of("id", "p1", "lastName", "Jones", VERSION_FIELD, versionOf(created))));
        assertNotEquals(versionOf(created), versionOf(updated));
        assertStoredVersion(definition, "p1", versionOf(updated));

        JsonNode read = findById(definition, context, "p1");
        assertEquals("Ann", read.get("firstName").asString(), "An update merges into the stored document");
        assertEquals("Jones", read.get("lastName").asString());

        Throwable stale = failure(update(definition, context, Map.of("id", "p1", "lastName", "Brown", VERSION_FIELD, versionOf(created))));
        assertVersionConflict(stale, "An update carrying a superseded version is refused");
    }

    @Test
    public void everyReadReturnsTheCurrentVersion() throws Exception {
        EntityDefinition definition = createVersionedPersonDefinition();
        EntityContext context = context();

        JsonNode created = await(save(definition, context, person("p1", "Ann", "Smith", null)));
        JsonNode updated = await(update(definition, context, Map.of("id", "p1", "lastName", "Jones", VERSION_FIELD, versionOf(created))));
        String current = versionOf(updated);
        runAsOrganization(() -> entitiesService.syncIndex(definition.getId(), context)).await();

        assertEquals(current, versionOf(findById(definition, context, "p1")), "findById");

        List<RawJson> byIds = runAsOrganization(() -> entitiesService.findByIds(definition.getId(), List.of("p1"), RawJson.class, context)).await();
        assertEquals(current, versionOf(json(byIds.getFirst())), "findByIds");

        Page<RawJson> all = runAsOrganization(() -> entitiesService.findAll(definition.getId(), Pageable.ofSize(10), RawJson.class, context)).await();
        assertEquals(1, all.getContent().size());
        assertEquals(current, versionOf(json(all.getContent().getFirst())), "findAll");

        Page<RawJson> searched = runAsOrganization(() -> entitiesService.search(definition.getId(), "lastName: Jones", Pageable.ofSize(10), RawJson.class, context)).await();
        assertEquals(1, searched.getContent().size());
        assertEquals(current, versionOf(json(searched.getContent().getFirst())), "search");
    }

    @Test
    public void bulkSaveCreatesWithoutVersionsAndChecksThem() throws Exception {
        EntityDefinition definition = createVersionedPersonDefinition();
        EntityContext context = context();

        bulkSave(definition, context, List.of(person("p1", "Ann", "Smith", null),
                                              person("p2", "Bob", "Smith", null),
                                              person("p3", "Cid", "Smith", null))).await();

        Map<String, String> versions = versionsOf(definition, context, List.of("p1", "p2", "p3"));
        for (String id : List.of("p1", "p2", "p3")) {
            assertStoredVersion(definition, id, versions.get(id));
        }

        Throwable duplicate = failure(bulkSave(definition, context, List.of(person("p1", "Ann", "Smith", null))));
        assertBulkVersionConflict(duplicate, "A bulk save without versions creates, so an existing id conflicts");

        bulkSave(definition, context, List.of(person("p1", "Ann", "Jones", versions.get("p1")),
                                              person("p2", "Bob", "Jones", versions.get("p2")))).await();

        Map<String, String> advanced = versionsOf(definition, context, List.of("p1", "p2", "p3"));
        assertNotEquals(versions.get("p1"), advanced.get("p1"));
        assertNotEquals(versions.get("p2"), advanced.get("p2"));
        assertEquals(versions.get("p3"), advanced.get("p3"), "An entity left out of the bulk keeps its version");
        assertEquals("Jones", findById(definition, context, "p1").get("lastName").asString());

        Throwable stale = failure(bulkSave(definition, context, List.of(person("p3", "Cid", "Jones", advanced.get("p3")),
                                                                        person("p1", "Ann", "Brown", versions.get("p1")))));
        assertBulkVersionConflict(stale, "One superseded version fails the bulk save");
        assertEquals("Jones", findById(definition, context, "p1").get("lastName").asString(), "The refused write changed nothing for the stale entity");
    }

    @Test
    public void bulkUpdateRequiresTheCurrentVersions() throws Exception {
        EntityDefinition definition = createVersionedPersonDefinition();
        EntityContext context = context();

        bulkSave(definition, context, List.of(person("p1", "Ann", "Smith", null),
                                              person("p2", "Bob", "Smith", null))).await();
        Map<String, String> versions = versionsOf(definition, context, List.of("p1", "p2"));

        Throwable missing = failure(bulkUpdate(definition, context, List.of(Map.of("id", "p1", "lastName", "Jones"))));
        assertInstanceOf(IllegalArgumentException.class, missing, "A bulk update without a version is refused before it reaches Elasticsearch");

        bulkUpdate(definition, context, List.of(Map.of("id", "p1", "lastName", "Jones", VERSION_FIELD, versions.get("p1")),
                                                Map.of("id", "p2", "lastName", "Jones", VERSION_FIELD, versions.get("p2")))).await();

        Map<String, String> advanced = versionsOf(definition, context, List.of("p1", "p2"));
        assertNotEquals(versions.get("p1"), advanced.get("p1"));
        assertNotEquals(versions.get("p2"), advanced.get("p2"));
        JsonNode read = findById(definition, context, "p1");
        assertEquals("Ann", read.get("firstName").asString(), "A bulk update merges into the stored document");
        assertEquals("Jones", read.get("lastName").asString());

        Throwable stale = failure(bulkUpdate(definition, context, List.of(Map.of("id", "p1", "lastName", "Brown", VERSION_FIELD, versions.get("p1")))));
        assertBulkVersionConflict(stale, "A bulk update carrying a superseded version is refused");
    }

    // ---- assertions ----

    /**
     * The version a write or read returns is the document's primary term and sequence number, and the
     * stored document carries no version field.
     */
    private void assertStoredVersion(EntityDefinition definition, String id, String version) throws Exception {
        GetResponse<Map> stored = esAsyncClient.get(g -> g.index(definition.getItemIndex())
                                                          .id(TENANT + "-" + id)
                                                          .routing(TENANT),
                                                    Map.class)
                                               .get(30, TimeUnit.SECONDS);
        assertTrue(stored.found(), "Document " + id + " is stored");
        assertEquals(stored.primaryTerm() + ":" + stored.seqNo(), version, "The version is the primary term and sequence number");
        assertFalse(stored.source().containsKey(VERSION_FIELD), "The version field is not part of the stored document");
    }

    private static void assertVersionConflict(Throwable failure, String message) {
        assertInstanceOf(ElasticsearchException.class, failure, message);
        assertEquals(409, ((ElasticsearchException) failure).status(), message);
    }

    private static void assertBulkVersionConflict(Throwable failure, String message) {
        assertInstanceOf(IllegalArgumentException.class, failure, message);
        assertTrue(failure.getMessage().contains("version conflict"), message + ": " + failure.getMessage());
    }

    // ---- fixtures ----

    private static EntityContext context() {
        return new DefaultEntityContext(applicationParticipant(TENANT, "user1"));
    }

    private EntityDefinition createVersionedPersonDefinition() throws Exception {
        ObjectC3Type schema = testDataService.createPersonSchema(MultiTenancyType.SHARED)
                                             .addProperty(VERSION_FIELD, new StringC3Type(), List.of(new VersionDecorator()));
        return await(runAsOrganization(() -> testDataService.createEntityDefinition("VersionedPerson_" + suffix(), schema)));
    }

    private static Map<String, Object> person(String id, String firstName, String lastName, String version) {
        Map<String, Object> person = new HashMap<>();
        person.put("id", id);
        person.put("firstName", firstName);
        person.put("lastName", lastName);
        person.put("addresses", List.of());
        person.put(VERSION_FIELD, version);
        return person;
    }

    private static String versionOf(JsonNode entity) {
        JsonNode version = entity.get(VERSION_FIELD);
        assertTrue(version != null && version.isString(), "The returned entity carries a version: " + entity);
        assertTrue(version.asString().matches("\\d+:\\d+"), "The version is of the shape primaryTerm:seqNo: " + version);
        return version.asString();
    }

    private Map<String, String> versionsOf(EntityDefinition definition, EntityContext context, List<String> ids) throws Exception {
        List<RawJson> found = await(runAsOrganization(() -> entitiesService.findByIds(definition.getId(), ids, RawJson.class, context)));
        assertEquals(ids.size(), found.size());
        Map<String, String> ret = new HashMap<>();
        for (RawJson rawJson : found) {
            JsonNode entity = json(rawJson);
            ret.put(entity.get("id").asString(), versionOf(entity));
        }
        return ret;
    }

    private JsonNode json(RawJson rawJson) {
        return objectMapper.readTree(rawJson.data());
    }

    private TokenBuffer buffer(Object value) {
        TokenBuffer buffer = new TokenBuffer(objectMapper._serializationContext(), false);
        buffer.writePOJO(value);
        return buffer;
    }

    private Future<JsonNode> save(EntityDefinition definition, EntityContext context, Map<String, ?> person) {
        return runAsOrganization(() -> entitiesService.save(definition.getId(), buffer(person), context)
                                                      .map(saved -> objectMapper.readTree(saved.asParser())));
    }

    private Future<JsonNode> update(EntityDefinition definition, EntityContext context, Map<String, ?> person) {
        return runAsOrganization(() -> entitiesService.update(definition.getId(), buffer(person), context)
                                                      .map(saved -> objectMapper.readTree(saved.asParser())));
    }

    private Future<Void> bulkSave(EntityDefinition definition, EntityContext context, List<? extends Map<String, ?>> people) {
        return runAsOrganization(() -> entitiesService.bulkSave(definition.getId(), buffer(new ArrayList<>(people)), context));
    }

    private Future<Void> bulkUpdate(EntityDefinition definition, EntityContext context, List<? extends Map<String, ?>> people) {
        return runAsOrganization(() -> entitiesService.bulkUpdate(definition.getId(), buffer(new ArrayList<>(people)), context));
    }

    private JsonNode findById(EntityDefinition definition, EntityContext context, String id) throws Exception {
        return json(await(runAsOrganization(() -> entitiesService.findById(definition.getId(), id, RawJson.class, context))));
    }

    /** The cause the operation failed with, or a failed assertion if it succeeded. */
    private static Throwable failure(Future<?> operation) {
        try {
            Object result = operation.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
            return fail("Expected the operation to fail but it returned: " + result);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (Exception e) {
            return fail("Expected the operation to fail through its future, not with: " + e);
        }
    }
}
