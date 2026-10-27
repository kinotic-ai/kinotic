package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalAuthenticationService implements LocalAuthenticationService {

    private final IdentityCredentialRepository credentialRepository;
    private final ParticipantIdentityRepository identityRepository;
    private final PasswordConfigurationService passwordConfigurationService;

    @Override
    public Future<SystemParticipantIdentity> authenticateSystemUser(String email, String password) {
        requireCredentials(email, password);
        return identityRepository.findSystemUserByEmail(email).compose(user -> verify(user, password));
    }

    @Override
    public Future<OrganizationParticipantIdentity> authenticateOrganizationUser(String email, String password, String organizationId) {
        requireCredentials(email, password);
        return passwordConfigurationService.findOffered(organizationId, null, null)
                                           .compose(offered -> offered == null
                                                   ? Future.succeededFuture()
                                                   : identityRepository.findOrganizationUserByEmail(email, organizationId).compose(user -> verify(user, password)));
    }

    @Override
    public Future<ApplicationParticipantIdentity> authenticateApplicationUser(String email, String password,
                                                                                String organizationId, String applicationId) {
        requireCredentials(email, password);
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        // the user's own tenant decides, so a sign-in that names no tenant, as a connection's headers do, finds it too
        return identityRepository.findApplicationUserByEmail(email, organizationId, applicationId)
                                 .compose(user -> user == null
                                         ? Future.succeededFuture()
                                         : passwordConfigurationService.findOffered(organizationId, applicationId, user.getTenantId())
                                                                       .compose(offered -> offered == null ? Future.succeededFuture() : verify(user, password)));
    }

    @Override
    public Future<Boolean> verifyPassword(UserParticipantIdentity user, String password) {
        Validate.notNull(user, "user cannot be null");
        Validate.notBlank(password, "password cannot be blank");
        return verify(user, password).map(verified -> verified != null);
    }

    private static void requireCredentials(String email, String password) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
    }

    // the user when it is enabled and password matches its credential, otherwise null
    private <T extends UserParticipantIdentity> Future<T> verify(T user, String password) {
        Future<T> ret;
        if (user == null || !user.isEnabled()) {
            ret = Future.succeededFuture();
        } else {
            ret = credentialRepository.findById(user.getId())
                                      .map(credential -> credential != null && DomainUtil.verifyPassword(password, credential.getSecretHash())
                                              ? user
                                              : null);
        }
        return ret;
    }
}
