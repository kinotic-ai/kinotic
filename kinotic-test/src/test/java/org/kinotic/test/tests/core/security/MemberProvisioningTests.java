package org.kinotic.test.tests.core.security;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.OrganizationMember;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.management.api.services.security.MemberService;
import org.kinotic.management.api.services.security.OwnedOidcConfigurationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how an organization's administrators add members and how those members first sign in: an address
 * becomes a pending member at once, with or without an invitation; the organization's own provider claims the
 * pending member it verifies under that address, once, and no one else; an invitation claims a pending member with a
 * password or a provider identity; an address is a member of an organization once, whatever users of other
 * organizations or the system it has; removing a pending member removes it; and an address added twice at once is
 * one member.
 */
@SpringBootTest
public class MemberProvisioningTests extends KinoticTestBase {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private MemberService members;

    @Autowired
    private OwnedOidcConfigurationService ownedConfigurations;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private InviteService invites;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private PasswordConfigurationService passwordConfigurations;

    // an invitation names an organization that exists, so each test adds members to one of its own, as its administrator
    private Organization organization;
    private OrganizationParticipant administrator;

    // the organization's own provider, through which a member added without an invitation signs in
    private OrganizationOidcConfiguration okta;

    @BeforeEach
    public void createOrganizationWithProvider() throws Exception {
        organization = await(organizations.create(new Organization().setName("Member provisioning " + suffix())
                                                                    .setDescription("Organization of one member provisioning test")));
        administrator = new DefaultOrganizationParticipant("member-admin-" + suffix(), organization.getId(),
                                                           Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                                  "displayName", "Member Admin"),
                                                           List.of("ADMIN"));
        OrganizationOidcConfiguration configuration = new OrganizationOidcConfiguration();
        configuration.setProviderAccount("acme.okta.com");
        configuration.setName("Acme Okta " + suffix())
                     .setProvider(OidcProviderType.OKTA)
                     .setClientId("acme-client")
                     .setEnabled(true);
        okta = (OrganizationOidcConfiguration) await(asAdministrator(() -> ownedConfigurations.save(configuration, null)));
        // invitations are accepted with a password as well
        await(asAdministrator(() -> passwordConfigurations.save(passwordSignIn(true))));
        // offered configurations are a search, which sees the row once the index has refreshed
        await(oidcConfigurations.syncIndex());
    }

    private <T> Future<T> asAdministrator(Supplier<Future<T>> call) {
        return runAs(administrator, call);
    }

    @Test
    public void aMemberAddedWithoutAnInvitationIsClaimedByTheOrganizationsProviderOnce() throws Exception {
        String email = "pending-" + suffix() + "@example.test";
        OrganizationMember added = await(asAdministrator(() -> members.addMember(email, "Pat Pending", false)));
        assertNotNull(added.id());
        assertTrue(added.pending());
        assertTrue(added.enabled());
        assertNull(await(invites.findPendingInvite(email, organization.getId())));
        assertFalse(await(identities.hasSignInMethod(added.id())));
        assertEquals(organization.getId(), memberOf(added));
        assertTrue(listed(email).pending());

        // the organization's provider claims the member under the verified email, binding the identity as the first
        // method; the organization user is found by a search, which sees it once the index has refreshed
        await(identities.syncIndex());
        Participant claimed = await(identities.claimProvisionedOrganizationUser(okta.getId(), "sub-" + suffix(), email, organization.getId()));
        assertNotNull(claimed);
        assertEquals(added.id(), claimed.getId());
        assertTrue(await(identities.hasSignInMethod(added.id())));
        assertFalse(listed(email).pending());

        // once claimed, no other identity under the same email takes the member, and no stranger's email finds one
        assertNull(await(identities.claimProvisionedOrganizationUser(okta.getId(), "sub-" + suffix(), email, organization.getId())));
        assertNull(await(identities.claimProvisionedOrganizationUser(okta.getId(), "sub-" + suffix(), "nobody-" + suffix() + "@example.test", organization.getId())));
    }

    @Test
    public void anAddressAddedTwiceAtOnceIsOneMember() throws Exception {
        String email = "twice-" + suffix() + "@example.test";
        // both calls look the address up before either organization user is written, so the index tells neither of the
        // other; the organization user's id is derived from the address, so the create of one of them is refused
        CompositeFuture both = await(asAdministrator(() -> {
            CompositeFuture joined = Future.join(members.addMember(email, "Pat Twice", false), members.addMember(email, "Pat Twice", false));
            return joined.transform(v -> Future.succeededFuture(joined));
        }));
        int added = 0;
        for (int i = 0; i < both.size(); i++) {
            if (both.succeeded(i)) {
                added++;
            } else {
                assertInstanceOf(IllegalArgumentException.class, both.cause(i));
            }
        }
        assertEquals(1, added, "exactly one addition succeeds");
        await(identities.syncIndex());
        Page<OrganizationMember> page = await(asAdministrator(() -> members.findMembers(Pageable.ofSize(1000))));
        assertEquals(added, page.getContent().stream().filter(member -> email.equals(member.email())).count());
    }

    @Test
    public void aSystemUsersAddressIsAddedAsANewOrganizationUser() throws Exception {
        String email = "system-user-" + suffix() + "@example.test";
        SystemParticipantIdentity systemUser = new SystemParticipantIdentity();
        systemUser.setEmail(email).setDisplayName("Sam System");
        await(identities.createUser(systemUser, PASSWORD));
        await(identities.syncIndex());

        // the organization adds a user of its own, pending, beside the system user
        OrganizationMember added = await(asAdministrator(() -> members.addMember(email, "Sam Member", false)));
        assertNotNull(added.id());
        assertNotEquals(systemUser.getId(), added.id());
        assertTrue(added.pending());
        assertEquals(organization.getId(), memberOf(added));
        assertTrue(await(identities.hasPassword(systemUser.getId())));
    }

    @Test
    public void aMemberIsClaimedOnlyWhereTheOrganizationAddedThem() throws Exception {
        // a member another organization added that nobody signs in as is no one's to claim here
        String email = "elsewhere-" + suffix() + "@example.test";
        OrganizationParticipantIdentity user = await(identities.createUser(organizationUser(email, "Added elsewhere", await(otherOrganization())), null));
        await(identities.syncIndex());
        assertNull(await(identities.claimProvisionedOrganizationUser(okta.getId(), "sub-" + suffix(), email, organization.getId())));
        assertFalse(await(identities.hasSignInMethod(user.getId())));
    }

    @Test
    public void passwordSignInTurnsOffOnceEveryMemberWithAPasswordSignsInThroughAProvider() throws Exception {
        String email = "connects-" + suffix() + "@example.test";
        OrganizationParticipantIdentity created = await(identities.createUser(organizationUser(email, "Pat Connects", organization), PASSWORD));
        // a member nobody signs in as yet is claimed through the provider, so it holds nothing back
        await(asAdministrator(() -> members.addMember("pending-" + suffix() + "@example.test", "Pat Pending", false)));

        ExecutionException refusal = assertThrows(ExecutionException.class, () -> await(asAdministrator(() -> passwordConfigurations.save(passwordSignIn(false)))));
        assertTrue(refusal.getCause().getMessage().contains(email), refusal.getCause().getMessage());

        await(identities.bindOidcIdentity(created.getId(), okta.getId(), "sub-" + suffix()));
        assertFalse(await(asAdministrator(() -> passwordConfigurations.save(passwordSignIn(false)))).isEnabled());
    }

    @Test
    public void theOrganizationsProviderStaysWhileItIsAMembersOnlyWayIn() throws Exception {
        String email = "okta-only-" + suffix() + "@example.test";
        OrganizationParticipantIdentity created = await(identities.createUser(organizationUser(email, "Pat Okta", organization), PASSWORD));
        await(identities.bindOidcIdentity(created.getId(), okta.getId(), "sub-" + suffix()));
        // the member signs in with the provider alone once the organization takes no password
        await(asAdministrator(() -> passwordConfigurations.save(passwordSignIn(false))));

        OrganizationOidcConfiguration disabled = new OrganizationOidcConfiguration();
        disabled.setProviderAccount(okta.getProviderAccount());
        disabled.setOrganizationId(organization.getId());
        disabled.setId(okta.getId());
        disabled.setName(okta.getName())
                .setProvider(OidcProviderType.OKTA)
                .setClientId(okta.getClientId())
                .setEnabled(false);
        ExecutionException refusal = assertThrows(ExecutionException.class, () -> await(asAdministrator(() -> ownedConfigurations.save(disabled, null))));
        assertTrue(refusal.getCause().getMessage().contains(email), refusal.getCause().getMessage());
        refusal = assertThrows(ExecutionException.class, () -> await(asAdministrator(() -> ownedConfigurations.deleteById(okta.getId()))));
        assertTrue(refusal.getCause().getMessage().contains(email), refusal.getCause().getMessage());

        // with the password taken again, the provider may go
        await(asAdministrator(() -> passwordConfigurations.save(passwordSignIn(true))));
        assertFalse(await(asAdministrator(() -> ownedConfigurations.save(disabled, null))).isEnabled());
    }

    private PasswordConfiguration passwordSignIn(boolean enabled) {
        return new PasswordConfiguration().setOrganizationId(organization.getId()).setEnabled(enabled);
    }

    @Test
    public void aMemberAddedWithAnInvitationClaimsTheOrganizationUserByAccepting() throws Exception {
        String withPassword = "accepts-" + suffix() + "@example.test";
        OrganizationMember added = await(asAdministrator(() -> members.addMember(withPassword, "Pat Accepts", true)));
        await(identities.syncIndex());
        PendingInvite invite = await(invites.findPendingInvite(withPassword, organization.getId()));
        assertNotNull(invite);
        await(asAdministrator(() -> members.sendInvitation(withPassword)));

        Participant member = await(invites.acceptLocalInvite(invite.getVerificationToken(), PASSWORD, "Pat Herself"));
        assertEquals(added.id(), member.getId());
        assertTrue(await(identities.hasPassword(added.id())));
        assertEquals("Pat Herself", ((UserParticipantIdentity) await(identities.findById(added.id()))).getDisplayName());
        assertFalse(listed(withPassword).pending());

        // or through one of the organization's providers, which binds the identity
        String withProvider = "binds-" + suffix() + "@example.test";
        OrganizationMember bound = await(asAdministrator(() -> members.addMember(withProvider, "Pat Binds", true)));
        await(identities.syncIndex());
        PendingInvite providerInvite = await(invites.findPendingInvite(withProvider, organization.getId()));
        Participant viaProvider = await(invites.acceptOidcInvite(providerInvite.getVerificationToken(), "sub-" + suffix(), okta.getId(), withProvider));
        assertEquals(bound.id(), viaProvider.getId());
        assertTrue(await(identities.hasSignInMethod(bound.id())));
    }

    @Test
    public void anAddressIsAMemberOfAnOrganizationOnce() throws Exception {
        String email = "existing-" + suffix() + "@example.test";
        // a user of another organization under the address is that organization's alone
        OrganizationParticipantIdentity elsewhere = await(identities.createUser(organizationUser(email, "Existing person", await(otherOrganization())), PASSWORD));
        await(identities.syncIndex());

        OrganizationMember added = await(asAdministrator(() -> members.addMember(email, null, false)));
        assertNotEquals(elsewhere.getId(), added.id());
        assertTrue(added.pending());
        assertEquals(organization.getId(), memberOf(added));

        // the address is a member here now, so it is not added again
        assertInstanceOf(ExistingUserException.class, failure(() -> members.addMember(email, null, true)));
    }

    @Test
    public void removingAPendingMemberRemovesIt() throws Exception {
        String email = "removed-" + suffix() + "@example.test";
        OrganizationMember added = await(asAdministrator(() -> members.addMember(email, "Pat Removed", true)));

        await(asAdministrator(() -> members.removeMember(added.id())));
        assertNull(await(identities.findById(added.id())));
        assertNull(await(invites.findPendingInvite(email, organization.getId())));

        // the address can be added again: the member takes the id the address derives, and nothing of the old one is left
        OrganizationMember again = await(asAdministrator(() -> members.addMember(email, "Pat Again", false)));
        assertEquals(added.id(), again.id());
        assertTrue(again.pending());
        assertFalse(await(identities.hasSignInMethod(again.id())));
    }

    @Test
    public void anInvitationIntoAnApplicationNamesOne() throws Exception {
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.inviteMember("someone-" + suffix() + "@example.test", null, null)));
    }

    // the member at email as the organization's members page lists them
    private OrganizationMember listed(String email) throws Exception {
        Page<OrganizationMember> page = await(asAdministrator(() -> members.findMembers(Pageable.ofSize(1000))));
        return page.getContent().stream().filter(member -> email.equals(member.email())).findFirst().orElseThrow();
    }

    // the organization the member's user belongs to
    private String memberOf(OrganizationMember member) throws Exception {
        return ((OrganizationParticipantIdentity) await(identities.findById(member.id()))).getOrganizationId();
    }

    private Future<Organization> otherOrganization() {
        return organizations.create(new Organization().setName("Member provisioning other " + suffix())
                                                      .setDescription("Another organization of one member provisioning test"));
    }

    private static OrganizationParticipantIdentity organizationUser(String email, String displayName, Organization of) {
        OrganizationParticipantIdentity ret = new OrganizationParticipantIdentity();
        ret.setOrganizationId(of.getId())
           .setEmail(email)
           .setDisplayName(displayName);
        return ret;
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(asAdministrator(call::get))).getCause();
    }
}
