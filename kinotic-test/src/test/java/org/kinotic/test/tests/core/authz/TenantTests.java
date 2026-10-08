package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.PendingInviteSummary;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.app.api.services.TenantService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.app.api.services.security.TenantMemberService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies an application's tenants end to end. A customer signs up to an application offering sign-up, which
 * creates the tenant, the customer as its first user and the customer's tenant admin grant in the application's
 * store, so the gateway admits the customer to the tenant services; the customer reads and renames the tenant,
 * invites a colleague, who accepts into the tenant, lists the tenant's users, grants the colleague a role and
 * revokes it, and removes the colleague; the colleague granted nothing is refused by the gateway. An application
 * isolating each user creates a tenant per user, which the user administers, and offers nothing else. And what
 * is refused: sign-up to an
 * application not offering it, a tenant name already taken, an invitation from an application not offering
 * invitations, a grant of an application's role, a grant to a user outside the tenant, and removing oneself.
 */
@SpringBootTest
public class TenantTests extends KinoticTestBase {

    private static final String TENANT_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.app.api.services.TenantService";
    private static final String MEMBER_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.app.api.services.security.TenantMemberService";
    private static final Pageable FIRST_PAGE = Pageable.create(0, 10, Sort.by("created"));

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private PendingSignUpRepository pendingSignUps;

    @Autowired
    private PendingInviteRepository pendingInvites;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantMemberService tenantMembers;

    @Autowired
    private ParticipantIdentityService identityService;

    @Test
    public void aCustomerSignsUpIntoATenantItThenAdministers() throws Exception {
        // an invitation reads its organization back, so the application belongs to an organization that has a record
        Organization organization = await(organizationService.createSync(new Organization().setName("Tenant Org " + suffix())
                                                                                          .setDescription("the tenant tests' organization")));
        String orgId = organization.getId();
        Participant organizationUser = new DefaultOrganizationParticipant("org-user-" + suffix(), orgId,
                                                                          Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                          List.of("USER"));
        String appId = "tenant-app-" + suffix();
        ApplicationKey key = new ApplicationKey(orgId, appId);
        Application application = await(runAs(organizationUser, () -> applicationService.createApplicationIfNotExist(
                appId, "Tenant application", EnumSet.of(OnboardingMechanism.TENANT_SIGN_UP, OnboardingMechanism.TENANT_INVITE))));
        // the verification link leads into the application's primary UI, which a deployment publishes
        await(applications.saveSync(application.setPrimaryUiUrl("https://" + appId + ".kinotic.test"), orgId));

        // the customer signs up: a pending record first, then the tenant with the customer as its administrator
        String email = "owner-" + suffix() + "@acme.test";
        await(signUpService.initiateTenantSignUp(key, email, "Acme Owner"));
        PendingSignUp pending = pendingSignUp(email, key);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUpService.initiateTenantSignUp(key, email, "Acme Owner")));
        UserParticipantIdentity owner = await(signUpService.completeTenantSignUp(pending.getVerificationToken(), "Acme Corp", "Acme-Pass-1"));
        assertEquals("acme-corp", owner.getTenantId());
        assertEquals(appId, owner.getApplicationId());
        Tenant tenant = await(tenants.findByTenantId(orgId, appId, "acme-corp"));
        assertNotNull(tenant, "the tenant was not created");
        assertEquals("Acme Corp", tenant.getName());
        assertEquals(owner.getId(), tenant.getCreatedBy());
        assertTrue(awaitUntil(() -> await(pendingSignUps.findByEmailAndApplication(email, orgId, appId)) == null),
                   "the pending sign-up outlived its completion");

        // the administrator is admitted to the tenant services once the store answers for its grant
        Participant admin = participant(orgId, appId, "acme-corp", owner.getId());
        assertTrue(awaitUntil(() -> admitted(MEMBER_SERVICE, "findMembers", admin, List.of(FIRST_PAGE))), "the administrator was never admitted");
        authorize(TENANT_SERVICE, "rename", admin, List.of("Acme Corporation"));
        assertEquals("Acme Corp", await(runAs(admin, tenantService::getTenant)).getName());
        assertEquals("Acme Corporation", await(runAs(admin, () -> tenantService.rename("Acme Corporation"))).getName());
        assertEquals(Set.of(owner.getId()), memberIds(admin));

        // a colleague is invited into the tenant, the invitation is cancelled and made again, and the colleague accepts
        String colleagueEmail = "colleague-" + suffix() + "@acme.test";
        PendingInviteSummary invitation = await(runAs(admin, () -> tenantMembers.inviteMember(colleagueEmail, "Colleague")));
        assertEquals("acme-corp", invitation.getTenantId());
        assertEquals(List.of(invitation.getId()), pendingInviteIds(admin));
        await(runAs(admin, () -> tenantMembers.cancelInvite(invitation.getId())));
        assertEquals(List.of(), pendingInviteIds(admin));
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.cancelInvite(invitation.getId())));
        await(runAs(admin, () -> tenantMembers.inviteMember(colleagueEmail, "Colleague")));
        PendingInvite stored = await(pendingInvites.findByEmailAndScope(colleagueEmail, orgId, appId));
        UserParticipantIdentity colleague = await(inviteService.acceptLocalInvite(stored.getVerificationToken(), "Colleague-1", null));
        assertEquals("acme-corp", colleague.getTenantId());
        assertEquals(Set.of(owner.getId(), colleague.getId()), memberIds(admin));

        // the colleague holds nothing on the tenant until granted; the administrator grants a role and revokes it
        Participant member = participant(orgId, appId, "acme-corp", colleague.getId());
        assertRefused(MEMBER_SERVICE, "findMembers", member, List.of(FIRST_PAGE), "tenant_can_view_members on tenant:acme-corp");
        Subject subject = new Subject(SubjectKind.USER, colleague.getId());
        List<RoleDefinition> roles = await(runAs(admin, tenantMembers::findRoles));
        String viewer = AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.VIEWER);
        assertTrue(roles.stream().anyMatch(role -> role.id().equals(viewer)), roles.toString());
        assertTrue(roles.stream().noneMatch(role -> role.id().startsWith(AuthzUtil.APPLICATION_TYPE + ".")), roles.toString());
        Grant grant = await(runAs(admin, () -> tenantMembers.grant(subject, viewer)));
        assertEquals(new Resource(AuthzUtil.TENANT_TYPE, "acme-corp"), grant.resource());
        assertTrue(grantIds(admin).contains(grant.id()));
        assertTrue(awaitUntil(() -> admitted(MEMBER_SERVICE, "findMembers", member, List.of(FIRST_PAGE))), "the viewer was never admitted");
        assertRefused(MEMBER_SERVICE, "removeMember", member, List.of(owner.getId()), "tenant_can_manage_members");
        await(runAs(admin, () -> tenantMembers.revoke(grant.id())));
        assertTrue(!grantIds(admin).contains(grant.id()));

        // what the services refuse
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.revoke(grant.id())));
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.grant(subject, AuthzUtil.roleId(AuthzUtil.APPLICATION_TYPE, AuthzUtil.ADMIN))));
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.grant(new Subject(SubjectKind.USER, "nobody"), viewer)));
        assertInstanceOf(IllegalArgumentException.class, failure(admin, () -> tenantMembers.removeMember(owner.getId())));
        String otherEmail = "other-" + suffix() + "@acme.test";
        await(signUpService.initiateTenantSignUp(key, otherEmail, "Other Owner"));
        String otherToken = pendingSignUp(otherEmail, key).getVerificationToken();
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUpService.completeTenantSignUp(otherToken, "Acme Corp", "Other-Pass-1")));

        // the colleague is removed
        await(runAs(admin, () -> tenantMembers.removeMember(colleague.getId())));
        assertEquals(Set.of(owner.getId()), memberIds(admin));
    }

    @Test
    public void anApplicationOfferingNeitherSignUpNorInvitationsRefusesThem() throws Exception {
        String appId = "closed-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Closed application", null)));

        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> signUpService.initiateTenantSignUp(new ApplicationKey(TEST_ORG_ID, appId), "customer@acme.test", "Customer")));
        UserParticipantIdentity user = endUser(appId, "closed-tenant");
        Participant caller = participant(TEST_ORG_ID, appId, "closed-tenant", user.getId());
        assertInstanceOf(IllegalStateException.class, failure(caller, () -> tenantMembers.inviteMember("colleague@acme.test", "Colleague")));
    }

    @Test
    public void anApplicationIsolatingEachUserCreatesATenantPerUserItsUserAdministers() throws Exception {
        String appId = "isolated-app-" + suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(
                appId, "Isolating application", EnumSet.of(OnboardingMechanism.TENANT_PER_USER))));

        UserParticipantIdentity user = endUser(appId, null);
        assertNotNull(user.getTenantId(), "the user received no tenant");
        Tenant tenant = await(tenants.findByTenantId(TEST_ORG_ID, appId, user.getTenantId()));
        assertNotNull(tenant, "the user's tenant was not created");
        assertEquals(user.getId(), tenant.getCreatedBy());
        assertEquals("Isolated User", tenant.getName());

        // the tenant is the user's alone, so the user administers it from the start: admitted to the tenant's
        // services, holding the tenant admin grant on it
        Participant owner = participant(TEST_ORG_ID, appId, user.getTenantId(), user.getId());
        assertTrue(awaitUntil(() -> admitted(MEMBER_SERVICE, "findMembers", owner, List.of(FIRST_PAGE))), "the user was never admitted to its tenant");
        assertEquals(Set.of(user.getId()), memberIds(owner));
        List<Grant> grants = await(runAs(owner, tenantMembers::findGrants));
        String admin = AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN);
        assertTrue(grants.stream().anyMatch(grant -> grant.roleId().equals(admin)
                                                     && grant.subject().equals(new Subject(SubjectKind.USER, user.getId()))
                                                     && grant.resource().equals(new Resource(AuthzUtil.TENANT_TYPE, user.getTenantId()))),
                   grants.toString());

        // a user created into a tenant that exists keeps what it was given, which is nothing
        UserParticipantIdentity guest = endUser(appId, user.getTenantId());
        assertEquals(user.getTenantId(), guest.getTenantId());
        assertRefused(MEMBER_SERVICE, "findMembers", participant(TEST_ORG_ID, appId, user.getTenantId(), guest.getId()), List.of(FIRST_PAGE),
                      "tenant_can_view_members on tenant:" + user.getTenantId());

        application.getOnboarding().add(OnboardingMechanism.TENANT_SIGN_UP);
        assertInstanceOf(IllegalArgumentException.class, failure(TEST_ORGANIZATION_PARTICIPANT, () -> applicationService.save(application)));
    }

    // The pending record a sign-up stores, once the index answers for it
    private PendingSignUp pendingSignUp(String email, ApplicationKey key) throws Exception {
        assertTrue(awaitUntil(() -> await(pendingSignUps.findByEmailAndApplication(email, key.organizationId(), key.applicationId())) != null),
                   "the sign-up of " + email + " was not recorded");
        return await(pendingSignUps.findByEmailAndApplication(email, key.organizationId(), key.applicationId()));
    }

    private Set<String> memberIds(Participant caller) throws Exception {
        Set<String> ret = new TreeSet<>();
        for (UserParticipantIdentity user : await(runAs(caller, () -> tenantMembers.findMembers(FIRST_PAGE))).getContent()) {
            ret.add(user.getId());
        }
        return ret;
    }

    private List<String> pendingInviteIds(Participant caller) throws Exception {
        return await(runAs(caller, () -> tenantMembers.findPendingInvites(FIRST_PAGE))).getContent().stream()
                                                                                       .map(PendingInviteSummary::getId)
                                                                                       .toList();
    }

    private List<String> grantIds(Participant caller) throws Exception {
        return await(runAs(caller, tenantMembers::findGrants)).stream().map(Grant::id).toList();
    }

    private UserParticipantIdentity endUser(String applicationId, String tenantId) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("isolated-" + suffix() + "@acme.test");
        user.setDisplayName("Isolated User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(applicationId);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "Isolated-1"));
    }

    private static Participant participant(String organizationId, String applicationId, String tenantId, String id) {
        return new DefaultApplicationParticipant(id, organizationId, applicationId, tenantId,
                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                 List.of("USER"));
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(call.get())).getCause();
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
