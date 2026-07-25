package org.kinotic.gateway.mcp;

import io.vertx.core.Future;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Header-driven authentication for the MCP e2e, mirroring the kinotic-server clienttest convention: scope is
 * derived from the {@code organizationId}/{@code applicationId} headers, so each participant type is one header
 * set away.
 */
@Primary
@Component
public class McpTestSecurityService implements SecurityService {

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        Map<String, String> authInfo = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (authenticationInfo != null) {
            authInfo.putAll(authenticationInfo);
        }

        String id = authInfo.getOrDefault("clientId", "anonymous");
        String organizationId = authInfo.get("organizationId");
        String applicationId = authInfo.get("applicationId");
        Map<String, String> metadata = Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                              ParticipantConstants.PARTICIPANT_TYPE_USER);

        Participant participant;
        if (organizationId == null) {
            participant = DefaultSystemParticipant.builder()
                                                  .id(id)
                                                  .metadata(metadata)
                                                  .roles(List.of("ADMIN"))
                                                  .build();
        } else if (applicationId == null) {
            participant = DefaultOrganizationParticipant.builder()
                                                        .id(id)
                                                        .organizationId(organizationId)
                                                        .metadata(metadata)
                                                        .roles(List.of("ADMIN"))
                                                        .build();
        } else {
            participant = DefaultApplicationParticipant.builder()
                                                       .id(id)
                                                       .organizationId(organizationId)
                                                       .applicationId(applicationId)
                                                       .tenantId(authInfo.get("tenantId"))
                                                       .metadata(metadata)
                                                       .roles(List.of("USER"))
                                                       .build();
        }
        return Future.succeededFuture(participant);
    }
}
