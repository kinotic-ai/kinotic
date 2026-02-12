

package org.kinotic.test.config;

import org.kinotic.rpc.api.exceptions.AuthenticationException;
import org.kinotic.rpc.api.security.DefaultParticipant;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.rpc.api.security.ParticipantConstants;
import org.kinotic.rpc.api.security.SecurityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provided to make testing without a configured IAM easier
 * WARNING: should not be used in production for any reason
 * Created by Navid Mitchell on 3/11/20
 */
@Component
@ConditionalOnProperty(prefix = "oidc-security-service", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DummySecurityService implements SecurityService {

    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        // These are the headers the Continuum JS client sends
        if (authenticationInfo.containsKey("login") && authenticationInfo.containsKey("passcode")) {
            String login = authenticationInfo.get("login");
            String password = authenticationInfo.get("passcode");
            if (login.equals("guest") && password.equals("guest")) {
                return CompletableFuture.completedFuture(new DefaultParticipant("kinotic-test",
                                                                                "guest",
                                                                                Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                       ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                                List.of("ADMIN")));
            }

        } else if (authenticationInfo.containsKey("authorization")) {

            // Header looks something like
            // "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
            String authorizationHeader = authenticationInfo.get("authorization");
            if (authorizationHeader != null) {
                String[] parts = authorizationHeader.split(" ");
                if (parts.length == 2 && "Basic".equalsIgnoreCase(parts[0])) {
                    String credentials = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    String[] creds = credentials.split(":", 2);
                    if (creds.length == 2) {
                        if (creds[0].equals("guest") && creds[1].equals("guest")) {

                            return CompletableFuture.completedFuture(new DefaultParticipant("kinotic",
                                                                                            "guest",
                                                                                            Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                                   ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                                            List.of("ADMIN")));
                        }
                    }
                }
            }
        }
        return CompletableFuture.failedFuture(new AuthenticationException("Invalid Authentication Credentials"));
    }

}
