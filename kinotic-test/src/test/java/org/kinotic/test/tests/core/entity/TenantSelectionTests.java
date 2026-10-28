package org.kinotic.test.tests.core.entity;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.domain.api.model.persistence.idl.TenantSelectionC3Type;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.model.persistence.idl.decorators.TenantIdDecorator;
import org.kinotic.persistence.api.services.AdminJsonEntitiesRepository;
import org.kinotic.management.api.services.NamedQueriesDefinitionService;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exercises a {@code SHARED} entity with a {@link TenantIdDecorator} field from both kinds of participant:
 * one that belongs to a tenant, and one connected at organization scope that names tenants through a
 * tenant selection the way the admin repository does.
 */
@SpringBootTest
public class TenantSelectionTests extends KinoticTestBase {

    private static final String COUNT_QUERY = "countPeople";
    private static final String TENANT_ID_FIELD = "tenantId";

    @Autowired
    private AdminJsonEntitiesRepository adminJsonEntitiesRepository;
    @Autowired
    private EntitiesService entitiesService;
    @Autowired
    private NamedQueriesDefinitionService namedQueriesDefinitionService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataService testDataService;

    @Test
    public void tenantedParticipantOwnsTheTenantIdField() {
        EntityDefinition definition = createTenantPersonDefinition("_ownsField");
        EntityContext tenant1 = new DefaultEntityContext(applicationParticipant("tenant1", "user1"));

        StepVerifier.create(Mono.fromCompletionStage(savePerson(definition, tenant1, "", "Blank").toCompletionStage()))
                    .expectNextMatches(saved -> "tenant1".equals(saved.get(TENANT_ID_FIELD).asString()))
                    .as("A blank tenant id is filled with the participant's tenant")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(savePerson(definition, tenant1, "tenant2", "Other").toCompletionStage()))
                    .as("Another tenant's id is rejected")
                    .expectError(IllegalArgumentException.class)
                    .verify();

        runAsOrganization(() -> entitiesService.syncIndex(definition.getId(), tenant1)).await();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), tenant1)).toCompletionStage()))
                    .expectNext(1L)
                    .as("The participant sees its own tenant")
                    .verifyComplete();
    }

    @Test
    public void tenantedParticipantIsConfinedToItsTenant() {
        EntityDefinition definition = createTenantPersonDefinition("_confined");
        EntityContext own = new DefaultEntityContext(applicationParticipant("tenant1", "user1")).setTenantSelection(List.of("tenant1"));
        EntityContext other = new DefaultEntityContext(applicationParticipant("tenant1", "user1")).setTenantSelection(List.of("tenant2"));
        EntityContext all = new DefaultEntityContext(applicationParticipant("tenant1", "user1")).setTenantSelection(List.of(EntityContext.ALL_TENANTS));

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), own)).toCompletionStage()))
                    .expectNext(0L)
                    .as("Selecting its own tenant is allowed")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), other)).toCompletionStage()))
                    .as("Selecting another tenant is refused")
                    .expectError(AuthorizationException.class)
                    .verify();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), all)).toCompletionStage()))
                    .as("Selecting every tenant is refused")
                    .expectError(AuthorizationException.class)
                    .verify();
    }

    @Test
    public void organizationParticipantReadsAndWritesAcrossTenants() {
        EntityDefinition definition = createTenantPersonDefinition("_acrossTenants");
        EntityContext organization = new DefaultEntityContext(TEST_ORGANIZATION_PARTICIPANT);

        StepVerifier.create(Mono.fromCompletionStage(savePerson(definition, organization, "", "Blank").toCompletionStage()))
                    .as("Without a tenant of its own the data must name one")
                    .expectError(IllegalArgumentException.class)
                    .verify();

        for (int i = 0; i < 2; i++) {
            savePerson(definition, organization, "tenant1", "One" + i).await();
        }
        for (int i = 0; i < 3; i++) {
            savePerson(definition, organization, "tenant2", "Two" + i).await();
        }
        runAsOrganization(() -> entitiesService.syncIndex(definition.getId(), organization)).await();

        // a save records the tenants it named on its context, so the unselected read gets a fresh one
        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), new DefaultEntityContext(TEST_ORGANIZATION_PARTICIPANT))).toCompletionStage()))
                    .as("A read without a tenant or a selection is refused")
                    .expectError(IllegalArgumentException.class)
                    .verify();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), selecting(organization, "tenant2"))).toCompletionStage()))
                    .expectNext(3L)
                    .as("A selection counts the named tenant")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.count(definition.getId(), selecting(organization, EntityContext.ALL_TENANTS))).toCompletionStage()))
                    .expectNext(5L)
                    .as("The wildcard counts every tenant")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.countByQuery(definition.getId(), "lastName: Two*", selecting(organization, EntityContext.ALL_TENANTS))).toCompletionStage()))
                    .expectNext(3L)
                    .as("The wildcard applies to a query")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(runAsOrganization(() -> entitiesService.findAll(definition.getId(), Pageable.ofSize(10), RawJson.class, selecting(organization, EntityContext.ALL_TENANTS))).toCompletionStage()))
                    .expectNextMatches(page -> page.getTotalElements() == 5 && page.getContent().size() == 5)
                    .as("The wildcard returns every tenant's rows")
                    .verifyComplete();
    }

    @Test
    public void saveRecordsTheTenantsNamedInTheDataOnItsContext() {
        EntityDefinition definition = createTenantPersonDefinition("_recordsSelection");
        EntityContext organization = new DefaultEntityContext(TEST_ORGANIZATION_PARTICIPANT).setTenantSelection(List.of("stale"));

        savePerson(definition, organization, "tenant1", "One").await();

        Assertions.assertEquals(List.of("tenant1"),
                                organization.getTenantSelection(),
                                "A save replaces the context's selection with the tenant the data names");

        EntityContext bulk = new DefaultEntityContext(TEST_ORGANIZATION_PARTICIPANT);
        List<Map<String, Object>> people = List.of(person("tenant2", "Two"), person("tenant1", "One2"), person("tenant2", "Two2"));
        try (TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false)) {
            tokenBuffer.writePOJO(people);
            runAsOrganization(() -> entitiesService.bulkSave(definition.getId(), tokenBuffer, bulk)).await();
        }

        Assertions.assertEquals(List.of("tenant2", "tenant1", "tenant2"),
                                bulk.getTenantSelection(),
                                "A bulk save records one entry per entity in data order");
    }

    @Test
    public void namedQueryTenantSelectionIsValidated() {
        EntityDefinition definition = createTenantPersonDefinition("_namedQuery");
        EntityContext organization = new DefaultEntityContext(TEST_ORGANIZATION_PARTICIPANT);
        for (int i = 0; i < 2; i++) {
            savePerson(definition, organization, "tenant1", "One" + i).await();
        }
        for (int i = 0; i < 3; i++) {
            savePerson(definition, organization, "tenant2", "Two" + i).await();
        }
        runAsOrganization(() -> entitiesService.syncIndex(definition.getId(), organization)).await();
        createCountQuery(definition);

        StepVerifier.create(Mono.fromCompletionStage(countWithAdminQuery(definition, TEST_ORGANIZATION_PARTICIPANT, "tenant1", "tenant2").toCompletionStage()))
                    .expectNext(5L)
                    .as("A participant without a tenant counts the tenants it selects")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(countWithAdminQuery(definition, applicationParticipant("tenant1", "user1"), "tenant1").toCompletionStage()))
                    .expectNext(2L)
                    .as("A tenanted participant may select its own tenant")
                    .verifyComplete();

        StepVerifier.create(Mono.fromCompletionStage(countWithAdminQuery(definition, applicationParticipant("tenant1", "user1"), "tenant1", "tenant2").toCompletionStage()))
                    .as("A tenanted participant selecting another tenant is refused")
                    .expectError(AuthorizationException.class)
                    .verify();

        StepVerifier.create(Mono.fromCompletionStage(countWithQueryParameter(definition, applicationParticipant("tenant1", "user1"), "tenant1", "tenant2").toCompletionStage()))
                    .as("A tenanted participant selecting another tenant through the query's parameter is refused")
                    .expectError(AuthorizationException.class)
                    .verify();
    }

    private void createCountQuery(EntityDefinition definition) {
        FunctionDefinition countPeople = new FunctionDefinition().setName(COUNT_QUERY)
                                                                 .addParameter("tenantSelection", new TenantSelectionC3Type());
        countPeople.setDecorators(List.of(new QueryDecorator().setStatements("SELECT COUNT(firstName) AS count FROM %s".formatted(definition.getName()))));

        NamedQueriesDefinition namedQueries = new NamedQueriesDefinition();
        namedQueries.setId(definition.getId());
        namedQueries.setOrganizationId(definition.getOrganizationId());
        namedQueries.setApplicationId(definition.getApplicationId());
        namedQueries.setProjectId(definition.getProjectId());
        namedQueries.setEntityDefinitionName(definition.getName());
        namedQueries.setNamedQueries(List.of(countPeople));
        runAsOrganization(() -> namedQueriesDefinitionService.saveSync(namedQueries)).await();
    }

    private Future<Long> countWithAdminQuery(EntityDefinition definition, ScopedParticipant participant, String... tenants) {
        return runAsOrganization(() -> adminJsonEntitiesRepository.namedQuery(definition.getId(),
                                                                              COUNT_QUERY,
                                                                              List.of(),
                                                                              List.of(tenants),
                                                                              participant))
                .map(rows -> objectMapper.readTree(rows.getFirst().data()).get("count").asLong());
    }

    @SuppressWarnings("rawtypes")
    private Future<Long> countWithQueryParameter(EntityDefinition definition, ScopedParticipant participant, String... tenants) {
        ListParameterHolder parameters = new ListParameterHolder(List.of(new QueryParameter("tenantSelection", List.of(tenants))));
        return runAsOrganization(() -> entitiesService.namedQuery(definition.getId(),
                                                                  COUNT_QUERY,
                                                                  parameters,
                                                                  Map.class,
                                                                  new DefaultEntityContext(participant)))
                .map(rows -> ((Number) rows.getFirst().get("count")).longValue());
    }

    private EntityContext selecting(EntityContext context, String... tenants) {
        return new DefaultEntityContext(context.getParticipant()).setTenantSelection(List.of(tenants));
    }

    private EntityDefinition createTenantPersonDefinition(String suffix) {
        ObjectC3Type schema = testDataService.createPersonSchema(MultiTenancyType.SHARED)
                                             .addProperty(TENANT_ID_FIELD, new StringC3Type(), List.of(new TenantIdDecorator()));
        return runAsOrganization(() -> testDataService.createEntityDefinition("TenantPerson" + suffix, schema)).await();
    }

    private Future<JsonNode> savePerson(EntityDefinition definition, EntityContext context, String tenantId, String lastName) {
        try (TokenBuffer tokenBuffer = new TokenBuffer(objectMapper._serializationContext(), false)) {
            tokenBuffer.writePOJO(person(tenantId, lastName));
            return runAsOrganization(() -> entitiesService.save(definition.getId(), tokenBuffer, context))
                    .map(saved -> objectMapper.readTree(saved.asParser()));
        }
    }

    private Map<String, Object> person(String tenantId, String lastName) {
        // the id field must be present for the generated id to be assigned
        Map<String, Object> person = new HashMap<>();
        person.put("id", null);
        person.put("firstName", "Test");
        person.put("lastName", lastName);
        person.put(TENANT_ID_FIELD, tenantId);
        person.put("addresses", List.of());
        return person;
    }
}
