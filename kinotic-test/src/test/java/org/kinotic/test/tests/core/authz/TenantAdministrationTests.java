package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.app.api.services.TenantAdministrationService;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.common.api.oidc.OidcProviderType;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a tenant's administrator runs its own tenant through {@link TenantAdministrationService} and nothing
 * beyond it: the {@code tenant.admin} role admits its holder to the service and to none of the rows; the
 * administrator grants its users the roles the application's developers marked for tenant administrators, and no
 * other; it invites, disables and removes the users of its tenant, and configures the tenant's own sign-in
 * provider; and a user of another tenant is out of its reach.
 */
@SpringBootTest
public class TenantAdministrationTests extends KinoticTestBase {

    private static final String ADMINISTRATION = DomainUtil.APP_API_ZONE + "~" + TenantAdministrationService.class.getName();
    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String TENANT_ADMIN = AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN);
    private static final String READING = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ);

    @Autowired
    private TenantAdministrationService administration;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private PasswordConfigurationService passwordConfigurations;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private InviteService invites;

    @Autowired
    private TestDataService testData;

    @BeforeEach
    public void createSampleApplication() throws Exception {
        await(runAsOrganization(() -> testData.createSampleProjectIfNotExist()));
    }

    @Test
    public void aTenantsAdministratorGrantsItsUsersTheRolesMarkedForIt() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        String tenantId = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity alice = endUser(tenantId);
        ApplicationParticipantIdentity bob = endUser(tenantId);
        String otherTenant = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity carol = endUser(otherTenant);
        Participant admin = applicationParticipant(tenantId, alice.getId());
        Participant member = applicationParticipant(tenantId, bob.getId());
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, tenantId);
        List<Object> personRow = List.of(person.getId(), "row-1");

        // a user holding nothing is refused the service
        assertRefused(ADMINISTRATION, "findUsers", admin, List.of(Map.of()), "tenant_can_read_members");
        Grant administers = await(runAsOrganization(() -> access.grant(TEST_APP_ID, user(alice), TENANT_ADMIN, tenant)));
        assertTrue(awaitUntil(() -> admitted(ADMINISTRATION, "findUsers", admin, List.of(Map.of()))), "the administrator was never admitted");
        authorize(ADMINISTRATION, "grant", admin, List.of(Map.of(), "role"));
        authorize(ADMINISTRATION, "inviteUser", admin, List.of("someone@kinotic.test", "Someone"));
        // administering a tenant gives none of its rows
        assertRefused(ENTITIES_SERVICE, "findById", admin, personRow, READING);

        RoleDefinition accountant = await(runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                null, "Accountant " + suffix(), null, false, null, null, Set.of(), Map.of(person.getId(), Set.of(READING)), true))));
        RoleDefinition staff = await(runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                null, "Staff " + suffix(), null, false, null, null, Set.of(READING), Map.of(), false))));
        assertTrue(accountant.tenantAssignable());
        assertFalse(staff.tenantAssignable());

        // the administrator sees the roles it may grant: the tenant's built-in ones and the ones marked for it
        Set<String> offered = Set.copyOf(await(runAs(admin, () -> administration.findRoles())).stream().map(RoleDefinition::id).toList());
        assertTrue(offered.containsAll(Set.of(TENANT_ADMIN, accountant.id())), offered.toString());
        assertFalse(offered.contains(staff.id()), offered.toString());
        assertFalse(offered.contains(AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.ADMIN)), offered.toString());

        Grant reads = await(runAs(admin, () -> administration.grant(user(bob), accountant.id())));
        assertEquals(tenant, reads.resource());
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", member, personRow)), "the role never admitted its holder");
        assertEquals(Set.of(administers, reads), Set.copyOf(await(runAs(admin, () -> administration.findGrants()))));
        // a role its developers kept, a built-in role of the rows, a user of another tenant and another tenant
        assertTrue(failure(admin, () -> administration.grant(user(bob), staff.id())).getMessage().contains("not one a tenant's administrators grant"));
        assertTrue(failure(admin, () -> administration.grant(user(bob), AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.ADMIN)))
                           .getMessage().contains("not one a tenant's administrators grant"));
        assertEquals("No user of the tenant has id " + carol.getId(), failure(admin, () -> administration.grant(user(carol), accountant.id())).getMessage());
        assertInstanceOfIllegalArgument(failure(admin, () -> administration.grant(new Subject(SubjectType.TENANT, otherTenant), accountant.id())));
        // the tenant's user holding no administration is refused the grant
        assertRefused(ADMINISTRATION, "grant", member, List.of(Map.of(), "role"), "tenant_can_manage_access");
        assertRefused(ENTITIES_SERVICE, "findById", applicationParticipant(otherTenant, carol.getId()), personRow, READING);

        // the administrator revokes what it may grant, and not its own grants
        assertTrue(failure(admin, () -> administration.revoke(administers.id())).getMessage().contains("made to yourself"));
        Grant kept = await(runAsOrganization(() -> access.grant(TEST_APP_ID, user(bob), staff.id(), tenant)));
        assertTrue(failure(admin, () -> administration.revoke(kept.id())).getMessage().contains("not one a tenant's administrators grant"));
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, tenant, kept.id())));
        await(runAs(admin, () -> administration.revoke(reads.id())));
        assertTrue(awaitUntil(() -> !admitted(ENTITIES_SERVICE, "findById", member, personRow)), "the revoked role still admitted its holder");
        // granted to the tenant, the role is held by every user of it
        Grant everyone = await(runAs(admin, () -> administration.grant(new Subject(SubjectType.TENANT, tenantId), accountant.id())));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", member, personRow)), "the tenant's grant never reached its user");

        await(runAs(admin, () -> administration.revoke(everyone.id())));
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, tenant, administers.id())));
        await(runAsOrganization(() -> access.deleteRole(TEST_APP_ID, accountant.id())));
        await(runAsOrganization(() -> access.deleteRole(TEST_APP_ID, staff.id())));
    }

    @Test
    public void aTenantsAdministratorInvitesDisablesAndRemovesTheUsersOfItsTenant() throws Exception {
        String tenantId = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity alice = endUser(tenantId);
        ApplicationParticipantIdentity bob = endUser(tenantId);
        ApplicationParticipantIdentity carol = endUser(createTenant(TEST_APP_ID));
        ApplicationParticipant admin = applicationParticipant(tenantId, alice.getId());
        String invited = "invited-" + suffix() + "@kinotic.test";

        // no one could accept an invitation while the tenant's login takes neither a password nor a provider
        assertTrue(failure(admin, () -> administration.inviteUser(invited, "Dana")).getMessage().contains("An invitation is accepted with a password"));
        await(runAsOrganization(() -> passwordConfigurations.save(new PasswordConfiguration().setOrganizationId(TEST_ORG_ID)
                                                                                           .setApplicationId(TEST_APP_ID)
                                                                                           .setTenantId(tenantId)
                                                                                           .setEnabled(true))));

        ApplicationParticipantIdentity dana = await(runAs(admin, () -> administration.inviteUser(invited, "Dana")));
        assertTrue(dana.isPending());
        assertEquals(tenantId, dana.getTenantId());
        assertEquals(tenantId, await(invites.findPendingInvite(invited, TEST_ORG_ID, TEST_APP_ID)).getTenantId());
        await(runAs(admin, () -> administration.resendInvitation(dana.getId())));
        assertTrue(failure(admin, () -> administration.resendInvitation(bob.getId())).getMessage().contains("already signed in"));

        await(identityService.syncIndex());
        Set<String> listed = Set.copyOf(await(runAs(admin, () -> administration.findUsers(Pageable.ofSize(100)))).getContent().stream()
                                                                                                             .map(ApplicationParticipantIdentity::getId)
                                                                                                             .toList());
        assertEquals(Set.of(alice.getId(), bob.getId(), dana.getId()), listed);

        // a user of another tenant is unknown to the administrator, and the administrator does not act on itself
        assertEquals("No user of the tenant has id " + carol.getId(), failure(admin, () -> administration.removeUser(carol.getId())).getMessage());
        assertEquals("No user of the tenant has id " + carol.getId(), failure(admin, () -> administration.setUserEnabled(carol.getId(), false)).getMessage());
        assertTrue(failure(admin, () -> administration.removeUser(alice.getId())).getMessage().contains("on yourself"));

        await(runAs(admin, () -> administration.setUserEnabled(bob.getId(), false)));
        assertFalse(((ApplicationParticipantIdentity) await(identityService.findById(bob.getId()))).isEnabled());
        await(runAs(admin, () -> administration.removeUser(bob.getId())));
        assertNull(await(identityService.findById(bob.getId())));
        await(runAs(admin, () -> administration.removeUser(dana.getId())));
        assertNull(await(invites.findPendingInvite(invited, TEST_ORG_ID, TEST_APP_ID)));
        assertNotNull(await(identityService.findById(carol.getId())));
    }

    @Test
    public void aTenantsAdministratorConfiguresTheSignInProviderOfItsTenantAlone() throws Exception {
        String tenantId = createTenant(TEST_APP_ID);
        String otherTenant = createTenant(TEST_APP_ID);
        ApplicationParticipant admin = applicationParticipant(tenantId, endUser(tenantId).getId());
        ApplicationParticipant otherAdmin = applicationParticipant(otherTenant, endUser(otherTenant).getId());

        // the scope is the caller's, whatever the configuration carried
        TenantOidcConfiguration saved = await(runAs(admin, () -> administration.saveSignInProvider(google().setTenantId(otherTenant), "a-secret")));
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(TEST_APP_ID, saved.getApplicationId());
        assertEquals(TEST_ORG_ID, saved.getOrganizationId());
        assertTrue(saved.isConfidentialClient());
        assertEquals(List.of(saved.getId()), await(runAs(admin, () -> administration.findSignInProviders())).stream().map(TenantOidcConfiguration::getId).toList());
        assertTrue(await(runAs(admin, () -> administration.findSignInProviderCallbackUrl(saved.getId()))).endsWith(saved.getId()));
        assertEquals(List.of(saved.getId()),
                     await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, TEST_APP_ID, tenantId)).stream()
                                                                                                      .filter(TenantOidcConfiguration.class::isInstance)
                                                                                                      .map(configuration -> configuration.getId())
                                                                                                      .toList());

        // another tenant's administrator neither sees it, replaces it nor deletes it
        assertTrue(await(runAs(otherAdmin, () -> administration.findSignInProviders())).isEmpty());
        assertNull(await(runAs(otherAdmin, () -> administration.findSignInProviderCallbackUrl(saved.getId()))));
        TenantOidcConfiguration theirs = google();
        theirs.setId(saved.getId());
        assertThrows(ExecutionException.class, () -> await(runAs(otherAdmin, () -> administration.saveSignInProvider(theirs, null))));
        await(runAs(otherAdmin, () -> administration.deleteSignInProvider(saved.getId())));
        assertNotNull(await(oidcConfigurations.findOwnedById(saved.getId(), TEST_ORG_ID)));

        await(runAs(admin, () -> administration.deleteSignInProvider(saved.getId())));
        assertNull(await(oidcConfigurations.findOwnedById(saved.getId(), TEST_ORG_ID)));
    }

    private static TenantOidcConfiguration google() {
        TenantOidcConfiguration ret = new TenantOidcConfiguration();
        ret.setName("Google")
           .setProvider(OidcProviderType.GOOGLE)
           .setClientId("tenant-google-client")
           .setEnabled(true);
        return ret;
    }

    private static Subject user(ApplicationParticipantIdentity identity) {
        return new Subject(SubjectType.USER, identity.getId());
    }

    private ApplicationParticipantIdentity endUser(String tenantId) throws Exception {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setEmail("tenant-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("Tenant User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "Tenant-User-1"));
    }

    private static void assertInstanceOfIllegalArgument(Throwable failure) {
        assertEquals(IllegalArgumentException.class, failure.getClass(), failure.toString());
    }

    private Throwable failure(Participant caller, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(caller, call::get))).getCause();
    }
}
