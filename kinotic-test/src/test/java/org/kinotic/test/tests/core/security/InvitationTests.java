package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invitations into an organization: an invitation is for someone who is not yet a member, and accepting one signs
 * in only a user of that organization, never a user of another scope with the same email, and takes a password only
 * where the organization allows password sign-in. A person in several organizations is a separate user of each. Also
 * covers the member search, which must list the organization's members alone.
 */
@SpringBootTest
public class InvitationTests extends KinoticTestBase {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String SYSTEM_USER_PASSWORD = "Staple-Battery-Horse-7";

    @Autowired
    private InviteService invites;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private LocalAuthenticationService localAuthentication;

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private PasswordConfigurationService passwordConfigurations;

    // an invitation names an organization that exists, so each test invites into one of its own, which allows password sign-in
    private Organization organization;

    @BeforeEach
    public void createOrganization() throws Exception {
        organization = await(createOrganization(true));
    }

    @Test
    public void anInvitationIsRefusedForAnEmailAlreadyAMemberOfTheOrganization() throws Exception {
        String email = "member-" + suffix() + "@example.test";
        await(identities.createUser(organizationUser(email, "Existing member", organization), PASSWORD));

        Throwable refusal = failure(() -> invites.createInvite(invite(email), null));
        assertInstanceOf(IllegalArgumentException.class, refusal);
        assertTrue(refusal.getMessage().contains("already exists in this organization"), refusal.getMessage());
    }

    @Test
    public void anInvitationAcceptedByADisabledMemberSignsNoOneIn() throws Exception {
        String email = "disabled-" + suffix() + "@example.test";
        // an administrator adds the member, invites them and disables them while the invitation is still out
        OrganizationParticipantIdentity added = organizationUser(email, "Disabled member", organization);
        added.setPending(true);
        OrganizationParticipantIdentity user = await(identities.createUser(added, null));
        PendingInvite invite = await(invites.createInvite(invite(email), null));
        await(identities.saveSync(user.setEnabled(false)));

        String token = invite.getVerificationToken();
        assertInstanceOf(UserDisabledException.class, failure(() -> invites.acceptLocalInvite(token, PASSWORD, null)));
        // a refused acceptance does not consume the invitation
        assertNotNull(await(invites.getValidInvite(token)));
    }

    @Test
    public void aSystemUsersEmailJoinsAnOrganizationAsAnOrganizationUserWithItsOwnPassword() throws Exception {
        String email = "both-" + suffix() + "@example.test";
        SystemParticipantIdentity systemUser = new SystemParticipantIdentity();
        systemUser.setEmail(email).setDisplayName("Sam System");
        await(identities.createUser(systemUser, SYSTEM_USER_PASSWORD));

        // the invitation sees no organization user for the address, so accepting it sets a new password
        PendingInvite invite = await(invites.createInvite(invite(email), null));
        Participant member = await(invites.acceptLocalInvite(invite.getVerificationToken(), PASSWORD, null));
        assertEquals(organization.getId(), assertInstanceOf(OrganizationParticipant.class, member).getOrganizationId());

        // each scope signs in with its own password, and neither reaches the other
        assertNotNull(await(localAuthentication.authenticateSystemUser(email, SYSTEM_USER_PASSWORD)));
        assertNull(await(localAuthentication.authenticateSystemUser(email, PASSWORD)));
        assertNotNull(await(localAuthentication.authenticateOrganizationUser(email, PASSWORD, organization.getId())));
        assertNull(await(localAuthentication.authenticateOrganizationUser(email, SYSTEM_USER_PASSWORD, organization.getId())));
    }

    @Test
    public void aPasswordSignsInOnlyWhereTheOrganizationAllowsPasswordSignIn() throws Exception {
        Organization providersOnly = await(createOrganization(false));
        String email = "password-" + suffix() + "@example.test";
        await(identities.createUser(organizationUser(email, "Pat Password", providersOnly), PASSWORD));

        assertNull(await(localAuthentication.authenticateOrganizationUser(email, PASSWORD, providersOnly.getId())));

        await(setPasswordSignIn(providersOnly, true));
        assertNotNull(await(localAuthentication.authenticateOrganizationUser(email, PASSWORD, providersOnly.getId())));
    }

    @Test
    public void aPersonInTwoOrganizationsIsASeparateUserOfEach() throws Exception {
        Organization other = await(createOrganization(true));
        String email = "two-orgs-" + suffix() + "@example.test";
        OrganizationParticipantIdentity here = await(identities.createUser(organizationUser(email, "Pat Here", organization), PASSWORD));
        OrganizationParticipantIdentity there = await(identities.createUser(organizationUser(email, "Pat There", other), SYSTEM_USER_PASSWORD));
        assertNotEquals(here.getId(), there.getId());

        // each password signs in to its own organization alone
        assertEquals(here.getId(), await(localAuthentication.authenticateOrganizationUser(email, PASSWORD, organization.getId())).getId());
        assertNull(await(localAuthentication.authenticateOrganizationUser(email, SYSTEM_USER_PASSWORD, organization.getId())));
        assertEquals(there.getId(), await(localAuthentication.authenticateOrganizationUser(email, SYSTEM_USER_PASSWORD, other.getId())).getId());
        assertNull(await(localAuthentication.authenticateOrganizationUser(email, PASSWORD, other.getId())));

        // a user acts in its own organization only
        assertNotNull(await(identities.resolveParticipant(here.getId(), organization.getId(), null)));
        assertNull(await(identities.resolveParticipant(here.getId(), other.getId(), null)));

        // one provider identity binds to a user of each organization
        String subject = "sub-" + suffix();
        await(identities.bindOidcIdentity(here.getId(), "github-platform", subject));
        await(identities.bindOidcIdentity(there.getId(), "github-platform", subject));
        assertEquals(here.getId(), await(identities.findOrganizationUserByOidcIdentity("github-platform", subject, organization.getId())).getId());
        assertEquals(there.getId(), await(identities.findOrganizationUserByOidcIdentity("github-platform", subject, other.getId())).getId());
        // a sign-in naming no organization finds the users of both
        assertEquals(Set.of(here.getId(), there.getId()),
                     await(identities.findOrganizationUsersByOidcIdentity("github-platform", subject)).stream()
                                                                                                     .map(OrganizationParticipantIdentity::getId)
                                                                                                     .collect(Collectors.toSet()));
    }

    @Test
    public void anOrganizationWithoutPasswordSignInTakesNoPasswordForItsInvitation() throws Exception {
        organization = await(createOrganization(false));
        String token = await(invites.createInvite(invite("no-password-" + suffix() + "@example.test"), null)).getVerificationToken();

        assertInstanceOf(IllegalArgumentException.class, failure(() -> invites.acceptLocalInvite(token, PASSWORD, null)));
        // the invitation stays out, to be accepted through one of the organization's providers
        assertNotNull(await(invites.getValidInvite(token)));
    }

    @Test
    public void passwordSignInIsNotTurnedOffWhileAMemberSignsInOnlyWithAPassword() throws Exception {
        String email = "only-password-" + suffix() + "@example.test";
        OrganizationParticipantIdentity user = await(identities.createUser(organizationUser(email, "Pat Only", organization), PASSWORD));

        Throwable refusal = failure(() -> setPasswordSignIn(organization, false));
        assertInstanceOf(IllegalStateException.class, refusal);
        assertTrue(refusal.getMessage().contains(email), refusal.getMessage());
        assertNotNull(await(passwordConfigurations.findOffered(organization.getId(), null, null)));
        // nor is it deleted
        String id = PasswordConfiguration.idOf(organization.getId(), null, null);
        assertInstanceOf(IllegalStateException.class, failure(() -> runAs(administratorOf(organization),
                                                                         () -> passwordConfigurations.deleteById(id, organization.getId()))));

        // a disabled member signs in nowhere, so it holds nothing back
        await(identities.saveSync(user.setEnabled(false)));
        await(setPasswordSignIn(organization, false));
        assertNull(await(passwordConfigurations.findOffered(organization.getId(), null, null)));
    }

    private Future<Organization> createOrganization(boolean passwordSignIn) {
        return organizations.create(new Organization().setName("Invitation tests " + suffix())
                                                      .setDescription("Organization of one invitation test"))
                            .compose(created -> passwordSignIn
                                    ? setPasswordSignIn(created, true).map(created)
                                    : Future.succeededFuture(created));
    }

    // the organization's own login takes a password while its configuration is enabled
    private Future<PasswordConfiguration> setPasswordSignIn(Organization target, boolean enabled) {
        return runAs(administratorOf(target), () -> passwordConfigurations.save(new PasswordConfiguration().setOrganizationId(target.getId())
                                                                                                           .setEnabled(enabled)));
    }

    private static OrganizationParticipant administratorOf(Organization target) {
        return new DefaultOrganizationParticipant("invitation-admin-" + target.getId(), target.getId(),
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                         "displayName", "Invitation Admin"),
                                                  List.of("ADMIN"));
    }

    @Test
    public void memberSearchIsHeldToTheOrganizationsMembers() throws Exception {
        // one token, so the search matches these two users and nothing another test created
        String name = "zq" + suffix();
        OrganizationParticipantIdentity member = await(identities.createUser(organizationUser("zq-member-" + suffix() + "@example.test", name, organization), PASSWORD));
        await(identities.createUser(organizationUser("zq-other-" + suffix() + "@example.test", name, await(createOrganization(true))), PASSWORD));

        // the search reads the index, which sees a write only once it has refreshed
        Page<OrganizationParticipantIdentity> found = awaitUntil(() -> identities.searchOrganizationUsers(name, organization.getId(), Pageable.ofSize(10)),
                                        page -> !page.getContent().isEmpty());
        assertEquals(List.of(member.getId()), found.getContent().stream().map(OrganizationParticipantIdentity::getId).toList());
        assertEquals(1, found.getTotalElements());
    }

    private PendingInvite invite(String email) {
        PendingInvite invite = new PendingInvite();
        invite.setEmail(email);
        invite.setDisplayName("Invitee");
        invite.setOrganizationId(organization.getId());
        invite.setInvitedByName("Invitation tests");
        return invite;
    }

    private static OrganizationParticipantIdentity organizationUser(String email, String displayName, Organization of) {
        OrganizationParticipantIdentity user = new OrganizationParticipantIdentity();
        user.setOrganizationId(of.getId());
        user.setEmail(email);
        user.setDisplayName(displayName);
        return user;
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }

    private static <T> T awaitUntil(Supplier<Future<T>> call, Predicate<T> done) throws Exception {
        T ret = await(call.get());
        for (int attempt = 0; attempt < 20 && !done.test(ret); attempt++) {
            Thread.sleep(500);
            ret = await(call.get());
        }
        return ret;
    }
}
