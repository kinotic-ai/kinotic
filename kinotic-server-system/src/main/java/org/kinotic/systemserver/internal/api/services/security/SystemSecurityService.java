package org.kinotic.systemserver.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.services.security.CredentialAuthenticationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The system server's {@link SecurityService}: admits platform operators, the clients they have authorized,
 * and the platform's own machines such as the vm-manager, each as a {@link SystemParticipant}. The credentials
 * of any other identity fail as invalid.
 */
@Component
@RequiredArgsConstructor
public class SystemSecurityService implements SecurityService {

    private final CredentialAuthenticationService credentialAuthenticationService;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        return credentialAuthenticationService.authenticate(authenticationInfo, SystemSecurityService::admits);
    }

    private static boolean admits(ParticipantIdentity identity) {
        return identity.getOrganizationId() == null && identity.getApplicationId() == null;
    }
}
