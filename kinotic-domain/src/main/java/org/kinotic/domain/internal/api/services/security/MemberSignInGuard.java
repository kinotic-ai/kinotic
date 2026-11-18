package org.kinotic.domain.internal.api.services.security;

import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
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
 * Refuses a change to what an organization's own login, or the system's, takes that would leave an enabled user who
 * signs in there today with no way in: turning off its password, or disabling or deleting one of its OIDC
 * configurations. The system's login is named by the system's id, {@code DomainUtil.SYSTEM_ID}.
 */
@Component
@RequiredArgsConstructor
public class MemberSignInGuard {

    // the most users of one login the guard reads
    private static final int MAX_MEMBERS = 10_000;

    private final IdentityCredentialRepository credentialRepository;
    private final OidcBindingRepository bindingRepository;
    private final ParticipantIdentityRepository identityRepository;
    private final OidcConfigurationRepository oidcRepository;
    private final PasswordConfigurationRepository passwordRepository;

    /**
     * Fails with an {@link IllegalStateException} naming the users who would be locked out of the login of
     * {@code organizationId} once it takes no password.
     */
    public Future<Void> requireMembersKeepSignInWithoutPassword(String organizationId) {
        return requireMembersKeepSignIn(organizationId, true, null);
    }

    /**
     * Fails with an {@link IllegalStateException} naming the users who would be locked out of the login of
     * {@code organizationId} once it no longer offers the OIDC configuration {@code configurationId}.
     */
    public Future<Void> requireMembersKeepSignInWithout(String organizationId, String configurationId) {
        return requireMembersKeepSignIn(organizationId, false, configurationId);
    }

    private Future<Void> requireMembersKeepSignIn(String organizationId, boolean removesPassword, String removedConfigurationId) {
        Future<List<? extends UserParticipantIdentity>> members = members(organizationId);
        Future<List<String>> offered = offeredConfigurationIds(organizationId);
        Future<PasswordConfiguration> password = passwordRepository.findById(PasswordConfiguration.idOf(organizationId, null, null), organizationId);
        return Future.all(members, offered, password)
                     .compose(v -> {
                         List<? extends UserParticipantIdentity> enabled = members.result().stream().filter(ParticipantIdentity::isEnabled).toList();
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
                                                      .map(UserParticipantIdentity::getEmail)
                                                      .sorted()
                                                      .collect(Collectors.joining(", "));
                             ret = Future.failedFuture(new IllegalStateException(
                                     "These users would have no way to sign in: " + emails
                                             + ". Each must first connect another of the sign-in methods its login offers."));
                         }
                         return ret;
                     });
    }

    private Future<List<? extends UserParticipantIdentity>> members(String organizationId) {
        Pageable all = Pageable.ofSize(MAX_MEMBERS);
        return DomainUtil.SYSTEM_ID.equals(organizationId)
                ? identityRepository.findSystemUsers(all).map(Page::getContent)
                : identityRepository.findOrganizationUsers(organizationId, all).map(Page::getContent);
    }

    // the configurations the login offers: its own enabled ones, and at an organization's the system's org sign-up registrations
    private Future<List<String>> offeredConfigurationIds(String organizationId) {
        Future<List<? extends OidcConfiguration>> signup = DomainUtil.SYSTEM_ID.equals(organizationId)
                ? Future.succeededFuture(List.of())
                : oidcRepository.findEnabledOrgSignup().map(found -> found);
        return Future.all(signup, oidcRepository.findEnabledOwnedByScope(organizationId, null, null))
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
