package org.kinotic.test;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.services.security.CredentialAuthenticationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The test node's {@link SecurityService}: every module runs in this one node, so it admits every identity
 * the credentials prove, whichever server would admit it in a deployment.
 */
@Component
@RequiredArgsConstructor
public class TestSecurityService implements SecurityService {

    private final CredentialAuthenticationService credentialAuthenticationService;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        return credentialAuthenticationService.authenticate(authenticationInfo, identity -> true);
    }
}
