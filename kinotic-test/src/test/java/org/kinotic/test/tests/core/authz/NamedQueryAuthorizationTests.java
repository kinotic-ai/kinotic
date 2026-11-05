package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.NamedQueriesDefinitionService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the request for a named query is checked for the permission its statement needs: a viewer of the
 * definition is admitted to a query that reads rows and refused one that updates them, with the reason, an editor
 * is admitted to the update, which then runs, and a query the definition does not hold is refused.
 */
@SpringBootTest
public class NamedQueryAuthorizationTests extends KinoticTestBase {

    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String RENAME_QUERY = "renamePeople";
    private static final String FIND_QUERY = "findPeople";

    @Autowired
    private ApplicationAccessService access;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EntitiesService entitiesService;
    @Autowired
    private EntityDefinitionService entityDefinitionService;
    @Autowired
    private ParticipantIdentityService identityService;
    @Autowired
    private NamedQueriesDefinitionService namedQueriesDefinitionService;
    @Autowired
    private TestDataService testDataService;

    @Test
    public void aNamedQueryIsCheckedForThePermissionItsStatementNeeds() throws Exception {
        EntityDefinition person = createPersonDefinition();
        createQueries(person);
        String tenantId = "tenant-" + suffix();
        ApplicationParticipantIdentity bob = endUser(tenantId);
        ScopedParticipant caller = applicationParticipant(tenantId, bob.getId());
        Subject subject = new Subject(SubjectType.USER, bob.getId());
        Resource personHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenantId));
        List<Object> find = List.of(person.getId(), FIND_QUERY, List.of());
        List<Object> rename = List.of(person.getId(), RENAME_QUERY, List.of());

        await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), personHere)));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "namedQuery", caller, find)), "the viewer was never admitted to the query that reads");
        assertRefused(ENTITIES_SERVICE, "namedQuery", caller, rename, "the query is an UPDATE");

        await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR), personHere)));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "namedQuery", caller, rename)), "the editor was never admitted to the update");
        assertEquals(0L, ((Number) await(rename(person, caller)).getFirst().get("count")).longValue());
        assertFalse(admitted(ENTITIES_SERVICE, "namedQuery", caller, List.of(person.getId(), "noSuchQuery", List.of())));
    }

    @SuppressWarnings("rawtypes")
    private Future<List<Map>> rename(EntityDefinition person, Participant caller) {
        ListParameterHolder parameters = new ListParameterHolder(List.of(new QueryParameter("oldName", "Nobody-" + suffix()),
                                                                         new QueryParameter("newName", "Renamed")));
        return runAsOrganization(() -> entitiesService.namedQuery(person.getId(), RENAME_QUERY, parameters, Map.class,
                                                                  new DefaultEntityContext((ScopedParticipant) caller)));
    }

    private void createQueries(EntityDefinition definition) throws Exception {
        FunctionDefinition find = new FunctionDefinition().setName(FIND_QUERY)
                                                          .addParameter("lastName", new StringC3Type());
        find.setDecorators(List.of(new QueryDecorator().setStatements("SELECT * FROM %s WHERE lastName = :lastName"
                                                                              .formatted(definition.getName()))));
        FunctionDefinition rename = new FunctionDefinition().setName(RENAME_QUERY)
                                                            .addParameter("oldName", new StringC3Type())
                                                            .addParameter("newName", new StringC3Type());
        rename.setDecorators(List.of(new QueryDecorator().setStatements("UPDATE %s SET lastName = :newName WHERE lastName = :oldName"
                                                                                .formatted(definition.getName()))));
        NamedQueriesDefinition namedQueries = new NamedQueriesDefinition();
        namedQueries.setId(definition.getId());
        namedQueries.setOrganizationId(definition.getOrganizationId());
        namedQueries.setApplicationId(definition.getApplicationId());
        namedQueries.setProjectId(definition.getProjectId());
        namedQueries.setEntityDefinitionName(definition.getName());
        namedQueries.setNamedQueries(List.of(find, rename));
        await(runAsOrganization(() -> namedQueriesDefinitionService.saveSync(namedQueries)));
    }

    private EntityDefinition createPersonDefinition() throws Exception {
        EntityDefinition definition = new EntityDefinition();
        definition.setName("NamedQueryPerson" + suffix());
        definition.setOrganizationId(TestDataService.SAMPLE_ORG_ID);
        definition.setApplicationId(TestDataService.SAMPLE_APP_ID);
        definition.setProjectId(TestDataService.SAMPLE_PROJECT_ID);
        definition.setDescription("Defines a Person that a named query renames");
        definition.setSchema(testDataService.createPersonSchema(MultiTenancyType.SHARED));
        return await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(TestDataService.SAMPLE_APP_ID, "Sample application", null)
                                                                 .compose(v -> entityDefinitionService.create(definition))
                                                                 .compose(saved -> entityDefinitionService.publish(saved.getId()).map(saved))));
    }

    private ApplicationParticipantIdentity endUser(String tenantId) throws Exception {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setEmail("end-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("End User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "End-User-1"));
    }
}
