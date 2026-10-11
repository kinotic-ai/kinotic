package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two applications of one organization share nothing through the entity functions: a user of one, granted a
 * definition's rows in its tenant and then every definition's rows in the whole application, is refused the
 * other application's definition of the same name, which its application's store does not hold; and a request
 * naming no definition at all is refused before the engine is asked.
 */
@SpringBootTest
public class ApplicationIsolationTests extends KinoticTestBase {

    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private TestDataService testData;

    @Test
    public void aUserOfOneApplicationIsRefusedAnotherApplicationsDefinitionOfTheSameName() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        Application other = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Isolated " + suffix(), "isolation", null)));
        // the other application's definition of the same name; whether it is published is immaterial to the check,
        // which reads no definition
        String theirs = DomainUtil.createEntityDefinitionId(new ApplicationKey(TEST_ORG_ID, other.getId()), person.getName());
        String tenantId = "tenant-" + suffix();
        UserParticipantIdentity bob = endUser(tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        Subject subject = new Subject(SubjectKind.USER, bob.getId());
        Resource personHere = new Resource(AuthzUtil.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenantId));
        Grant here = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzUtil.ENTITY_DEFINITION_TYPE, AuthzUtil.EDITOR), personHere)));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", caller, List.of(person.getId(), "row-1"))),
                   "the editor was never admitted to its own application's rows");

        assertRefused(ENTITIES_SERVICE, "findById", caller, List.of(theirs, "row-1"), theirs);
        assertRefused(ENTITIES_SERVICE, "findAll", caller, List.of(theirs, Map.of("pageNumber", 0, "pageSize", 10)), theirs);
        assertRefused(ENTITIES_SERVICE, "bulkUpdate", caller, List.of(theirs, List.of(Map.of("id", "row-1"))), theirs);

        // a grant on the whole application reaches every definition it holds, and still none it does not
        Resource application = new Resource(AuthzUtil.APPLICATION_TYPE, TEST_APP_ID);
        Grant everywhere = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzUtil.ENTITY_DEFINITION_TYPE, AuthzUtil.ADMIN), application)));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "deleteById", caller, List.of(person.getId(), "row-1"))),
                   "the admin granted on the application was never admitted");
        assertRefused(ENTITIES_SERVICE, "findById", caller, List.of(theirs, "row-1"), theirs);
        // a value that is no definition at all
        String nonsense = TEST_ORG_ID + "." + TEST_APP_ID + ".not a definition";
        assertRefused(ENTITIES_SERVICE, "count", caller, List.of(nonsense), "names no resource");

        // the sample application is shared with the other suites, which list its grants
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, application, everywhere.id())));
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, personHere, here.id())));
        await(runAsOrganization(() -> applicationService.deleteById(other.getId())));
    }

    private UserParticipantIdentity endUser(String tenantId) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("isolated-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("Isolated User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "Isolated-1"));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
