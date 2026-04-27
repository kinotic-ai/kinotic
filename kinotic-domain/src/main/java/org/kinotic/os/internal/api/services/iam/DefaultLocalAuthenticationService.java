package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalAuthenticationService implements LocalAuthenticationService {

    private final IamUserService iamUserService;
    private final IamCredentialService credentialStore;

    @Override
    public CompletableFuture<IamUser> authenticateLocal(String email, String password) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        return iamUserService.findByEmailPrimary(email)
                             .thenCompose(user -> {
                                 if (user == null
                                         || user.getAuthType() != AuthType.LOCAL
                                         || !user.isEnabled()) {
                                     return CompletableFuture.completedFuture(null);
                                 }
                                 return credentialStore.findById(user.getId())
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
