package org.kinotic.orgserver.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.CredentialAuthenticationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The org server's {@link SecurityService}: admits an organization's users, the clients they have authorized,
 * and its machines other than application runtimes, each as an {@link OrganizationParticipant}. The credentials
 * of any other identity fail as invalid.
 */
@Component
@RequiredArgsConstructor
public class OrganizationSecurityService implements SecurityService {

    private final CredentialAuthenticationService credentialAuthenticationService;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        return credentialAuthenticationService.authenticate(authenticationInfo, OrganizationSecurityService::admits);
    }

    private static boolean admits(ParticipantIdentity identity) {
        return identity.getOrganizationId() != null
                && identity.getApplicationId() == null
                // a runtime publishes into its application's zone, which only the app server hosts
                && !(identity instanceof MachineParticipantIdentity machine
                        && machine.getMachineKind() == MachineKind.APP_RUNTIME);
    }
}
