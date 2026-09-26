package org.kinotic.appserver.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.net.HostAndPort;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.CredentialAuthenticationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The app server's {@link SecurityService}: admits an application's users, the clients they have authorized and
 * its machines, each as an {@link ApplicationParticipant}, and the runtime workloads of an organization's
 * applications as an {@link OrganizationParticipant}. A request addressed to an application's API host admits
 * only that application's identities and its organization's runtimes. The credentials of any other identity
 * fail as invalid.
 */
@Component
@RequiredArgsConstructor
public class ApplicationSecurityService implements SecurityService {

    private final CredentialAuthenticationService credentialAuthenticationService;
    private final KinoticDomainProperties domainProperties;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        AppHost appHost = requestAppHost(authenticationInfo);
        return credentialAuthenticationService.authenticate(authenticationInfo, identity -> admits(identity, appHost));
    }

    private static boolean admits(ParticipantIdentity identity, AppHost appHost) {
        boolean ret;
        if (identity.getOrganizationId() == null) {
            ret = false;
        } else if (identity.getApplicationId() != null) {
            ret = appHost == null || appHost.equals(new AppHost(identity.getOrganizationId(), identity.getApplicationId()));
        } else {
            // an organization's identity reaches the app server only as the runtime of one of its applications
            ret = identity instanceof MachineParticipantIdentity machine
                    && machine.getMachineKind() == MachineKind.APP_RUNTIME
                    && (appHost == null || appHost.organizationId().equals(machine.getOrganizationId()));
        }
        return ret;
    }

    // null when the request was addressed to the server's own address rather than an application's API host
    private AppHost requestAppHost(Map<String, String> authenticationInfo) {
        String host = authenticationInfo.entrySet().stream()
                                        .filter(header -> HttpHeaders.HOST.toString().equalsIgnoreCase(header.getKey()))
                                        .map(Map.Entry::getValue)
                                        .findFirst()
                                        .orElse(null);
        HostAndPort authority = host != null ? HostAndPort.parseAuthority(host, -1) : null;
        return authority != null ? domainProperties.getDomain().resolveAppHost(authority.host()) : null;
    }
}
