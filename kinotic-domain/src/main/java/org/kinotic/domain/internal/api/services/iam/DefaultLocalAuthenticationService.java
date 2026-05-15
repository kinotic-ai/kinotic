package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.api.services.iam.LocalAuthenticationService;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalAuthenticationService implements LocalAuthenticationService {

    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;

    @Override
    public CompletableFuture<IamUser> authenticateLocal(String email, String password) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        return verifyMatchingUser(password, () -> iamUserRepository.findByEmail(email));
    }

    @Override
    public CompletableFuture<IamUser> authenticateLocal(String email, String password,
                                                        String authScopeType, String authScopeId) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        Validate.notBlank(authScopeId, "authScopeId cannot be blank");
        return verifyMatchingUser(password, () -> iamUserRepository.findByEmailAndScope(email, authScopeType, authScopeId));
    }

    private CompletableFuture<IamUser> verifyMatchingUser(String password,
                                                          Supplier<CompletableFuture<IamUser>> lookup) {
        return lookup.get().thenCompose(user -> {
            if (user == null
                    || user.getAuthType() != AuthType.LOCAL
                    || !user.isEnabled()) {
                return CompletableFuture.completedFuture(null);
            }
            return credentialRepository.findById(user.getId())
                                  .thenApply(credential -> {
                                      if (credential == null
                                              || !DomainUtil.verifyPassword(password, credential.getPasswordHash())) {
                                          return null;
                                      }
                                      return user;
                                  });
        });
    }
}
