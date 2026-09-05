package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalAuthenticationService implements LocalAuthenticationService {

    private final IdentityCredentialRepository credentialRepository;
    private final ParticipantIdentityRepository identityRepository;

    @Override
    public Future<UserParticipantIdentity> authenticateLocal(String email, String password) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        return verifyMatchingUser(password, () -> identityRepository.findByEmail(email));
    }

    @Override
    public Future<UserParticipantIdentity> authenticateLocal(String email,
                                                             String password,
                                                             String organizationId,
                                                             String applicationId) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        if (applicationId != null) {
            Validate.notBlank(organizationId, "organizationId is required when applicationId is supplied");
        }
        return verifyMatchingUser(password, () -> identityRepository.findByEmail(email, organizationId, applicationId));
    }

    private Future<UserParticipantIdentity> verifyMatchingUser(String password,
                                                               Supplier<Future<UserParticipantIdentity>> lookup) {
        return lookup.get().compose(user -> {
            if (user == null
                    || user.getAuthType() != AuthType.LOCAL
                    || !user.isEnabled()) {
                return Future.succeededFuture(null);
            }
            return credentialRepository.findById(user.getId())
                                  .map(credential -> {
                                      if (credential == null
                                              || !DomainUtil.verifyPassword(password, credential.getSecretHash())) {
                                          return null;
                                      }
                                      return user;
                                  });
        });
    }
}
