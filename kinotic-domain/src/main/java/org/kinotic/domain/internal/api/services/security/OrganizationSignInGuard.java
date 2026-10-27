package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.internal.api.model.IdentityCredential;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.internal.api.repositories.OidcBindingRepository;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.repositories.PasswordConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refuses a change to what an organization's own login takes that would leave an enabled member who signs in there
 * today with no way in: turning off its password, or disabling or deleting one of its OIDC configurations.
 */
@Component
@RequiredArgsConstructor
public class OrganizationSignInGuard {

    // the most members of one organization the guard reads
    private static final int MAX_MEMBERS = 10_000;

    private final IdentityCredentialRepository credentialRepository;
    private final OidcBindingRepository bindingRepository;
    private final ParticipantIdentityRepository identityRepository;
    private final OidcConfigurationRepository oidcRepository;
    private final PasswordConfigurationRepository passwordRepository;

    /**
     * Fails with an {@link IllegalStateException} naming the members who would be locked out of organization
     * {@code organizationId} once its login takes no password.
     */
    public Future<Void> requireMembersKeepSignInWithoutPassword(String organizationId) {
        return requireMembersKeepSignIn(organizationId, true, null);
    }

    /**
     * Fails with an {@link IllegalStateException} naming the members who would be locked out of organization
     * {@code organizationId} once its login no longer offers the OIDC configuration {@code configurationId}.
     */
    public Future<Void> requireMembersKeepSignInWithout(String organizationId, String configurationId) {
        return requireMembersKeepSignIn(organizationId, false, configurationId);
    }

    private Future<Void> requireMembersKeepSignIn(String organizationId, boolean removesPassword, String removedConfigurationId) {
        Future<List<OrganizationParticipantIdentity>> members = identityRepository.findOrganizationUsers(organizationId, Pageable.ofSize(MAX_MEMBERS))
                                                                                  .map(Page::getContent);
        Future<List<String>> offered = offeredConfigurationIds(organizationId);
        Future<PasswordConfiguration> password = passwordRepository.findById(PasswordConfiguration.idOf(organizationId, null, null), organizationId);
        return Future.all(members, offered, password)
                     .compose(v -> {
                         List<OrganizationParticipantIdentity> enabled = members.result().stream().filter(ParticipantIdentity::isEnabled).toList();
                         List<String> memberIds = enabled.stream().map(ParticipantIdentity::getId).toList();
                         boolean passwordBefore = password.result() != null && password.result().isEnabled();
                         boolean passwordAfter = passwordBefore && !removesPassword;
                         Set<String> offeredBefore = new HashSet<>(offered.result());
                         Set<String> offeredAfter = new HashSet<>(offeredBefore);
                         offeredAfter.remove(removedConfigurationId);
                         return Future.all(credentialRepository.findAllById(memberIds), bindingRepository.findByIdentities(memberIds))
                                      .map(methods -> {
                                          Set<String> withPassword = methods.<List<IdentityCredential>>resultAt(0).stream()
                                                                            .map(IdentityCredential::getId)
                                                                            .collect(Collectors.toSet());
                                          List<OidcBinding> bindings = methods.resultAt(1);
                                          return enabled.stream()
                                                        .filter(member -> signsIn(member.getId(), passwordBefore, offeredBefore, withPassword, bindings)
                                                                && !signsIn(member.getId(), passwordAfter, offeredAfter, withPassword, bindings))
                                                        .toList();
                                      });
                     })
                     .compose(lockedOut -> {
                         Future<Void> ret;
                         if (lockedOut.isEmpty()) {
                             ret = Future.succeededFuture();
                         } else {
                             String emails = lockedOut.stream()
                                                      .map(OrganizationParticipantIdentity::getEmail)
                                                      .sorted()
                                                      .collect(Collectors.joining(", "));
                             ret = Future.failedFuture(new IllegalStateException(
                                     "These members would have no way to sign in to the organization: " + emails
                                             + ". Each must first connect another of the sign-in methods its login offers."));
                         }
                         return ret;
                     });
    }

    // the configurations the organization's own login offers: the system's org sign-up registrations and its own enabled ones
    private Future<List<String>> offeredConfigurationIds(String organizationId) {
        return Future.all(oidcRepository.findEnabledOrgSignup(), oidcRepository.findEnabledOwnedByScope(organizationId, null, null))
                     .map(v -> v.<List<? extends OidcConfiguration>>list().stream()
                                .flatMap(List::stream)
                                .map(OidcConfiguration::getId)
                                .toList());
    }

    private static boolean signsIn(String memberId, boolean passwordTaken, Set<String> offered, Set<String> withPassword, List<OidcBinding> bindings) {
        return (passwordTaken && withPassword.contains(memberId))
                || bindings.stream().anyMatch(binding -> binding.getIdentityId().equals(memberId) && offered.contains(binding.getOidcConfigId()));
    }
}
