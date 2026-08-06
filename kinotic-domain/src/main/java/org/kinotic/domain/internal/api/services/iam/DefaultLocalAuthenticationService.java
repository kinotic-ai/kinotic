package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.UserParticipantIdentity;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.api.services.iam.LocalAuthenticationService;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalAuthenticationService implements LocalAuthenticationService {

    private final IdentityCredentialRepository credentialRepository;
    private final ParticipantIdentityRepository identityRepository;

    @Override
    public CompletableFuture<UserParticipantIdentity> authenticateLocal(String email, String password) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        return verifyMatchingUser(password, () -> identityRepository.findByEmail(email));
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> authenticateLocal(String email,
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

    private CompletableFuture<UserParticipantIdentity> verifyMatchingUser(String password,
                                                          Supplier<CompletableFuture<UserParticipantIdentity>> lookup) {
        return lookup.get().thenCompose(user -> {
            if (user == null
                    || user.getAuthType() != AuthType.LOCAL
                    || !user.isEnabled()) {
                return CompletableFuture.completedFuture(null);
            }
            return credentialRepository.findById(user.getId())
                                  .thenApply(credential -> {
                                      if (credential == null
                                              || !DomainUtil.verifyPassword(password, credential.getSecretHash())) {
                                          return null;
                                      }
                                      return user;
                                  });
        });
    }
}
