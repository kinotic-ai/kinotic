package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.management.api.model.SignInMethod;
import org.kinotic.management.api.services.security.ProfileService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A member's own sign-in methods: a password is set, listed and counted as a way in only while the organization's
 * login takes one.
 */
@SpringBootTest
public class ProfileServiceTests extends KinoticTestBase {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private ProfileService profiles;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private LocalAuthenticationService localAuthentication;

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private PasswordConfigurationService passwordConfigurations;

    @Test
    public void aMemberSetsAPasswordWhereTheOrganizationTakesOne() throws Exception {
        Organization organization = await(createOrganization(true));
        OrganizationParticipantIdentity user = await(createProviderUser(organization));
        OrganizationParticipant member = participantOf(user);

        await(runAs(member, () -> profiles.setPassword(null, PASSWORD, member)));

        List<SignInMethod> methods = await(runAs(member, () -> profiles.findMySignInMethods(member)));
        assertTrue(methods.stream().anyMatch(method -> SignInMethod.PASSWORD_ID.equals(method.id())));
        assertNotNull(await(localAuthentication.authenticateOrganizationUser(user.getEmail(), PASSWORD, organization.getId())));
    }

    @Test
    public void aMemberSetsNoPasswordWhereTheOrganizationTakesNone() throws Exception {
        Organization organization = await(createOrganization(false));
        OrganizationParticipant member = participantOf(await(createProviderUser(organization)));

        Throwable refusal = assertThrows(ExecutionException.class,
                                         () -> await(runAs(member, () -> profiles.setPassword(null, PASSWORD, member)))).getCause();
        assertInstanceOf(IllegalArgumentException.class, refusal);
        assertFalse(await(identities.hasPassword(member.getId())));
    }

    @Test
    public void aPasswordTheOrganizationTakesNoneOfIsNoWayIn() throws Exception {
        Organization organization = await(createOrganization(false));
        OrganizationParticipantIdentity user = await(identities.createUser(organizationUser(organization), PASSWORD));
        OidcBinding binding = await(identities.bindOidcIdentity(user.getId(), "github-platform", "sub-" + suffix()));
        OrganizationParticipant member = participantOf(user);

        List<SignInMethod> methods = await(runAs(member, () -> profiles.findMySignInMethods(member)));
        assertEquals(List.of(binding.getId()), methods.stream().map(SignInMethod::id).toList());

        // the binding is the member's only way in, so it stays
        Throwable refusal = assertThrows(ExecutionException.class,
                                         () -> await(runAs(member, () -> profiles.removeSignInMethod(binding.getId(), member)))).getCause();
        assertInstanceOf(IllegalArgumentException.class, refusal);
        assertEquals(1, await(identities.findOidcBindings(user.getId())).size());
    }

    // a member that signs in by the system's GitHub registration alone
    private Future<OrganizationParticipantIdentity> createProviderUser(Organization organization) {
        return identities.createUser(organizationUser(organization), null)
                         .compose(user -> identities.bindOidcIdentity(user.getId(), "github-platform", "sub-" + suffix()).map(user));
    }

    private Future<Organization> createOrganization(boolean passwordSignIn) {
        return organizations.create(new Organization().setName("Profile tests " + suffix())
                                                      .setDescription("Organization of one profile test"))
                            .compose(created -> passwordSignIn
                                    ? runAs(administratorOf(created),
                                            () -> passwordConfigurations.save(new PasswordConfiguration().setOrganizationId(created.getId())
                                                                                                         .setEnabled(true)))
                                            .map(created)
                                    : Future.succeededFuture(created));
    }

    private static OrganizationParticipantIdentity organizationUser(Organization of) {
        OrganizationParticipantIdentity user = new OrganizationParticipantIdentity();
        user.setOrganizationId(of.getId());
        user.setEmail("profile-" + suffix() + "@example.test");
        user.setDisplayName("Pat Profile");
        return user;
    }

    private static OrganizationParticipant participantOf(OrganizationParticipantIdentity user) {
        return new DefaultOrganizationParticipant(user.getId(), user.getOrganizationId(), userMetadata(user.getDisplayName()), List.of());
    }

    private static OrganizationParticipant administratorOf(Organization target) {
        return new DefaultOrganizationParticipant("profile-admin-" + target.getId(), target.getId(),
                                                  userMetadata("Profile Admin"), List.of("ADMIN"));
    }

    private static Map<String, String> userMetadata(String displayName) {
        return Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                      "displayName", displayName);
    }
}
