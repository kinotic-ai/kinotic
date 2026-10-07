package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.app.api.services.security.TenantMemberService;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two organizations that name an application alike get the same application id, and share nothing through it:
 * each application has its own authorization store and record, an organization's members reach only its own
 * application, its grants are neither listed nor revoked from the other, a tenant admin of one acts on no user of
 * the other's tenant of the same id, and deleting one application leaves the other's store and everything in it.
 */
@SpringBootTest
public class OrganizationIsolationTests extends KinoticTestBase {

    private static final String TENANT_ID = "acme";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private TenantMemberService tenantMembers;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private PendingSignUpRepository pendingSignUps;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzStoreRepository stores;

    @Test
    public void twoOrganizationsNamingAnApplicationAlikeShareNothingThroughIt() throws Exception {
        DefaultOrganizationParticipant other = otherOrganization();
        String otherOrganizationId = other.getOrganizationId();
        String name = "Isolated " + suffix();
        Application ours = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(name, "isolation", null)));
        Application theirs = await(runAs(other, () -> applicationService.createApplicationIfNotExist(name, "isolation", null)));
        String applicationId = ours.getId();
        assertEquals(applicationId, theirs.getId());
        String ourStore = DomainUtil.authzApplicationId(TEST_ORG_ID, applicationId);
        String theirStore = DomainUtil.authzApplicationId(otherOrganizationId, applicationId);

        // a record and a store each
        assertEquals(TEST_ORG_ID, await(stores.findById(ourStore)).getOrganizationId());
        assertEquals(otherOrganizationId, await(stores.findById(theirStore)).getOrganizationId());
        assertNotNull(await(storeService.modelId(ourStore)));
        assertNotNull(await(storeService.modelId(theirStore)));

        // the other organization's admin holds its own application and not ours
        String platformModel = await(storeService.modelId(AuthzStoreService.PLATFORM));
        String theirAdmin = AuthzUtil.object(AuthzUtil.USER_TYPE, other.getId());
        assertTrue(await(relationships.check(AuthzStoreService.PLATFORM, platformModel,
                                             new RelationshipTuple(theirAdmin, "application_can_edit", AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, theirStore)),
                                             Consistency.HIGHER_CONSISTENCY)));
        assertFalse(await(relationships.check(AuthzStoreService.PLATFORM, platformModel,
                                              new RelationshipTuple(theirAdmin, "application_can_edit", AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, ourStore)),
                                              Consistency.HIGHER_CONSISTENCY)));

        // a tenant of the same id in each application, administered in ours
        UserParticipantIdentity ourAdmin = endUser(TEST_ORG_ID, applicationId);
        UserParticipantIdentity theirUser = endUser(otherOrganizationId, applicationId);
        Resource tenant = new Resource(AuthzUtil.TENANT_TYPE, TENANT_ID);
        Grant grant = await(runAsOrganization(() -> access.grant(applicationId, user(ourAdmin), AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN), tenant)));

        // the other organization neither sees our grant nor revokes it
        assertTrue(await(runAs(other, () -> access.findGrants(applicationId, tenant))).isEmpty());
        assertInstanceOf(IllegalArgumentException.class, failure(other, () -> access.revoke(applicationId, tenant, grant.id())));
        assertEquals(List.of(grant), await(runAsOrganization(() -> access.findGrants(applicationId, tenant))));
        // and grants nothing in its application to our user
        assertInstanceOf(IllegalArgumentException.class,
                         failure(other, () -> access.grant(applicationId, user(ourAdmin), AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN), tenant)));

        // our tenant's admin acts on no user of their tenant of the same id
        Participant admin = new DefaultApplicationParticipant(ourAdmin.getId(), TEST_ORG_ID, applicationId, TENANT_ID,
                                                              Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                     ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                              List.of("USER"));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(admin, () -> tenantMembers.grant(user(theirUser), AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN))));
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.removeMember(theirUser.getId())));
        assertNotNull(await(identityService.findById(theirUser.getId())));

        // deleting ours leaves theirs with everything in it
        await(runAsOrganization(() -> applicationService.deleteById(applicationId)));
        assertNull(await(stores.findById(ourStore)));
        assertNotNull(await(stores.findById(theirStore)));
        assertNotNull(await(storeService.modelId(theirStore)));
        assertTrue(await(relationships.holds(theirStore, new RelationshipTuple(AuthzUtil.object(AuthzUtil.USER_TYPE, theirUser.getId()),
                                                                               AuthzUtil.MEMBER_RELATION,
                                                                               AuthzUtil.object(AuthzUtil.TENANT_TYPE, TENANT_ID)))));
        await(runAs(other, () -> applicationService.deleteById(applicationId)));
    }

    // An organization of its own, signed up as a customer does, with its creator as its admin
    private DefaultOrganizationParticipant otherOrganization() throws Exception {
        String suffix = suffix();
        String token = UUID.randomUUID().toString();
        PendingSignUp pending = new PendingSignUp();
        pending.setId(UUID.randomUUID().toString());
        pending.setVerificationToken(token);
        pending.setCreated(new Date());
        pending.setExpiresAt(new Date(System.currentTimeMillis() + 3_600_000));
        pending.setEmail("isolation-admin-" + suffix + "@kinotic.test");
        pending.setDisplayName("Isolation Admin");
        pending.setAuthType(AuthType.LOCAL);
        await(pendingSignUps.saveSync(pending));
        UserParticipantIdentity creator = await(signUpService.completeLocalSignUp(token, "Isolation Org " + suffix, "isolation", "Isolation-1"));
        return new DefaultOrganizationParticipant(creator.getId(), creator.getOrganizationId(),
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                  List.of("ADMIN"));
    }

    private UserParticipantIdentity endUser(String organizationId, String applicationId) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("isolated-" + suffix() + "@kinotic.test");
        user.setDisplayName("Isolated User");
        user.setOrganizationId(organizationId);
        user.setApplicationId(applicationId);
        user.setTenantId(TENANT_ID);
        return await(identityService.createUser(user, "End-User-1"));
    }

    private static Subject user(UserParticipantIdentity identity) {
        return new Subject(SubjectKind.USER, identity.getId());
    }

    private Throwable failure(Participant caller, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(caller, call::get))).getCause();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
