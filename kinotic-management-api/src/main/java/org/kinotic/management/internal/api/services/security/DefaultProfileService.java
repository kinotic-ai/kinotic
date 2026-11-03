package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.LoginProviderSummary;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.SignInMethod;
import org.kinotic.management.api.services.security.ProfileService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultProfileService implements ProfileService {

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final PasswordConfigurationService passwordConfigurationService;

    @Override
    public Future<UserParticipantIdentity> findMyProfile(Participant participant) {
        return loadCallingUser(participant);
    }

    @Override
    public Future<UserParticipantIdentity> updateDisplayName(String displayName, Participant participant) {
        Validate.notBlank(displayName, "displayName is required");
        return loadCallingUser(participant)
                .compose(user -> identityService.save(user.setDisplayName(displayName)))
                .map(UserParticipantIdentity.class::cast);
    }

    @Override
    public Future<List<SignInMethod>> findMySignInMethods(Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        Future<Boolean> signsInWithPassword = signsInWithPassword(participant);
        Future<List<SignInMethod>> bindings = identityService.findOidcBindings(participant.getId())
                .compose(found -> oidcConfigurationService.findAllById(found.stream().map(OidcBinding::getOidcConfigId).toList())
                                                          .map(configurations -> methodsOf(found, configurations)));
        return Future.all(signsInWithPassword, bindings).map(cf -> {
            List<SignInMethod> methods = new ArrayList<>();
            if (signsInWithPassword.result()) {
                methods.add(new SignInMethod(SignInMethod.PASSWORD_ID, AuthType.PASSWORD, "Password", null, null));
            }
            methods.addAll(bindings.result());
            return methods;
        });
    }

    // the bindings as methods, each named by its configuration, in the bindings' order; a binding whose
    // configuration is gone is left out
    private static List<SignInMethod> methodsOf(List<OidcBinding> bindings, List<OidcConfiguration> configurations) {
        Map<String, OidcConfiguration> byId = configurations.stream().collect(Collectors.toMap(OidcConfiguration::getId, Function.identity()));
        return bindings.stream()
                       .filter(binding -> byId.containsKey(binding.getOidcConfigId()))
                       .map(binding -> {
                           OidcConfiguration configuration = byId.get(binding.getOidcConfigId());
                           return new SignInMethod(binding.getId(), AuthType.OIDC, configuration.getName(),
                                                   configuration.getProvider(), binding.getCreated());
                       })
                       .toList();
    }

    @Override
    public Future<List<LoginProviderSummary>> findConnectableProviders(Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        // a system user signs in with a password alone
        Future<List<LoginProviderSummary>> ret;
        if (participant instanceof OrganizationParticipant member) {
            Future<Set<String>> bound = identityService.findOidcBindings(member.getId())
                                                       .map(bindings -> bindings.stream().map(OidcBinding::getOidcConfigId).collect(Collectors.toSet()));
            Future<List<OidcConfiguration>> offered = oidcConfigurationService.findEnabledForScope(member.getOrganizationId(), null);
            ret = Future.all(bound, offered).map(cf -> offered.result().stream()
                                                              .filter(configuration -> !bound.result().contains(configuration.getId()))
                                                              .map(LoginProviderSummary::of)
                                                              .toList());
        } else {
            // a system user signs in with a password alone
            ret = Future.succeededFuture(List.of());
        }
        return ret;
    }

    @Override
    public Future<Void> setPassword(String currentPassword, String newPassword, Participant participant) {
        Validate.notBlank(newPassword, "newPassword is required");
        return loadCallingUser(participant)
                .compose(user -> passwordOffered(participant).map(offered -> {
                    Validate.isTrue(offered, "Your organization's login takes no password.");
                    return user;
                }))
                .compose(user -> identityService.hasPassword(user.getId()).compose(hasPassword -> {
                    Future<Void> proven;
                    if (!hasPassword) {
                        // the session is proof enough for a user with no password yet
                        proven = Future.succeededFuture();
                    } else {
                        Validate.notBlank(currentPassword, "currentPassword is required");
                        proven = localAuthenticationService.verifyPassword(user, currentPassword)
                                                           .map(verified -> {
                                                               Validate.isTrue(verified, "The current password is incorrect.");
                                                               return null;
                                                           });
                    }
                    return proven;
                }))
                .compose(v -> identityService.setPassword(participant.getId(), newPassword));
    }

    @Override
    public Future<Void> removeSignInMethod(String methodId, Participant participant) {
        Validate.notBlank(methodId, "methodId is required");
        Validate.isTrue(!SignInMethod.PASSWORD_ID.equals(methodId), "A password is changed, not removed.");
        DomainUtil.requireUserParticipant(participant);
        Future<Boolean> signsInWithPassword = signsInWithPassword(participant);
        Future<List<OidcBinding>> bindings = identityService.findOidcBindings(participant.getId());
        // a binding signs the user in while its organization's login offers the binding's configuration
        Future<List<OidcConfiguration>> offered = participant instanceof OrganizationParticipant member
                ? oidcConfigurationService.findEnabledForScope(member.getOrganizationId(), null)
                : Future.succeededFuture(List.of());
        return Future.all(signsInWithPassword, bindings, offered).compose(cf -> {
            Validate.isTrue(bindings.result().stream().anyMatch(binding -> binding.getId().equals(methodId)), "Sign-in method not found.");
            Set<String> offeredIds = offered.result().stream().map(OidcConfiguration::getId).collect(Collectors.toSet());
            boolean signsIn = signsInWithPassword.result()
                              || bindings.result().stream()
                                         .anyMatch(binding -> !binding.getId().equals(methodId) && offeredIds.contains(binding.getOidcConfigId()));
            Validate.isTrue(signsIn, "The last sign-in method that still signs you in cannot be removed.");
            return identityService.removeOidcBinding(participant.getId(), methodId);
        });
    }

    // whether the calling user has a password its login takes
    private Future<Boolean> signsInWithPassword(Participant participant) {
        Future<Boolean> hasPassword = identityService.hasPassword(participant.getId());
        Future<Boolean> offered = passwordOffered(participant);
        return Future.all(hasPassword, offered).map(cf -> hasPassword.result() && offered.result());
    }

    // a system user signs in with a password alone; an organization's user only while its organization's login takes one
    private Future<Boolean> passwordOffered(Participant participant) {
        Future<Boolean> ret;
        if (participant instanceof OrganizationParticipant member) {
            ret = passwordConfigurationService.findOffered(member.getOrganizationId(), null, null).map(Objects::nonNull);
        } else {
            ret = Future.succeededFuture(true);
        }
        return ret;
    }

    /** Loads the user behind the calling participant. */
    private Future<UserParticipantIdentity> loadCallingUser(Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return identityService.findById(participant.getId())
                              .map(identity -> {
                                  // a session outlives its row when the user is deleted mid-session
                                  if (!(identity instanceof UserParticipantIdentity user)) {
                                      throw new IllegalArgumentException("Profile not found.");
                                  }
                                  return user;
                              });
    }
}
