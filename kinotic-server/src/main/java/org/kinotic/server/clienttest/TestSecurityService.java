package org.kinotic.server.clienttest;

import io.vertx.core.Future;
import org.kinotic.core.api.security.KinoticAudience;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link SecurityService} used only under the {@code clienttest} profile, where domain and
 * os-api are disabled and the real {@code KinoticSecurityService} (which resolves users from
 * Elasticsearch) is absent. It authenticates purely from the STOMP CONNECT headers with no
 * user store, so the {@code @kinotic-ai/core} suite can exercise the RPC mechanism in
 * isolation. The Kinotic CLI participant id maps to the ANONYMOUS role; every other client
 * maps to ADMIN. Scope (System/Organization/Application) is derived from the
 * {@code organizationId}/{@code applicationId} headers, matching the real service.
 */
@Component
@Profile("clienttest")
public class TestSecurityService implements SecurityService {

    private static final String CLI_PARTICIPANT_ID = "-42-Kinotic-CLI-42-";

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo, KinoticAudience audience) {
        // STOMP preserves header case while HTTP callers lowercase; a case-insensitive view
        // lets both work with the same camelCase names.
        Map<String, String> authInfo = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (authenticationInfo != null) {
            authInfo.putAll(authenticationInfo);
        }

        String clientId = authInfo.get("clientId");
        String organizationId = authInfo.get("organizationId");
        String applicationId = authInfo.get("applicationId");

        String id = clientId != null ? clientId : "anonymous";
        List<String> roles = CLI_PARTICIPANT_ID.equals(clientId) ? List.of("ANONYMOUS") : List.of("ADMIN");
        Map<String, String> metadata = Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                               ParticipantConstants.PARTICIPANT_TYPE_USER);

        Participant participant;
        if (organizationId == null) {
            participant = DefaultSystemParticipant.builder()
                                                  .id(id)
                                                  .metadata(metadata)
                                                  .roles(roles)
                                                  .build();
        } else if (applicationId == null) {
            participant = DefaultOrganizationParticipant.builder()
                                                        .id(id)
                                                        .organizationId(organizationId)
                                                        .metadata(metadata)
                                                        .roles(roles)
                                                        .build();
        } else {
            participant = DefaultApplicationParticipant.builder()
                                                       .id(id)
                                                       .organizationId(organizationId)
                                                       .applicationId(applicationId)
                                                       .tenantId(authInfo.get("tenantId"))
                                                       .metadata(metadata)
                                                       .roles(roles)
                                                       .build();
        }
        return Future.succeededFuture(participant);
    }
}
