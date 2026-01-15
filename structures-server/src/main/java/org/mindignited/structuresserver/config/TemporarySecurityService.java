package org.mindignited.structuresserver.config;

import org.kinotic.continuum.api.security.DefaultParticipant;
import org.kinotic.continuum.api.security.ParticipantConstants;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.api.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.security.sasl.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(prefix = "oidc-security-service", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TemporarySecurityService implements SecurityService {


    private static final Logger log = LoggerFactory.getLogger(TemporarySecurityService.class);

    private static final String PASSWORD = "structures";
    private static final Participant participant = new DefaultParticipant("mindignited",
                                                                          "admin",
                                                                          Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                 ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                          List.of("ADMIN"));

    /**
     * Authenticates a user based on the provided authenticationInfo
     * This currently uses a hard coded username and password for demonstration purposes
     * Username: admin
     * Password: structures
     *
     * This also supports Basic Auth. The username and password are base64 encoded and sent in the Authorization header
     * {
     *   "Authorization":"Basic YWRtaW46c3RydWN0dXJlcw=="
     * }
     *
     * @param authenticationInfo a map of authentication information. The keys and values are specific to the implementation
     * @return a CompletableFuture that will complete with the authenticated participant or complete exceptionally with an AuthenticationException
     */
    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        // we need to bring in a JWT library and verify the token
        // however we should look at supporting audience and issuer validations
        // but to do this we need to store those values with the organization 
        // we might need to know if a request for auth is coming from an application or the management UI. 
        if(authenticationInfo.containsKey("login") && Objects.equals(authenticationInfo.get("login"), "admin")
            && authenticationInfo.containsKey("passcode") && Objects.equals(authenticationInfo.get("passcode"), PASSWORD)){
            log.debug("Successfully authenticated user with continuum credentials");

            return CompletableFuture.completedFuture(createParticipant(authenticationInfo.get("tenantId")));

        }else if (authenticationInfo.containsKey("authorization")){
            String authorizationHeader = authenticationInfo.get("authorization");
            // Header looks something like
            // "Authorization: Basic YWRtaW46c3RydWN0dXJlcw=="
            String[] parts = authorizationHeader.split(" ");
            if (parts.length == 2 && "Basic".equalsIgnoreCase(parts[0])) {
                String credentials = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                String[] creds = credentials.split(":", 2);
                if (creds.length == 2) {
                    if (creds[0].equals("admin") && creds[1].equals(PASSWORD)) {

                        log.debug("Successfully authenticated user with basic auth");

                        return CompletableFuture.completedFuture(createParticipant(authenticationInfo.get("tenantId")));
                    }else{
                        log.debug("Failed to authenticate user: {}", authenticationInfo.get("login"));
                    }
                }
            }else{
                log.debug("Only Basic Auth is supported. Received {}", authorizationHeader);
            }
        }

        return CompletableFuture.failedFuture(new AuthenticationException("username/password pair provided was not correct."));
    }

    private Participant createParticipant(String tenantId){
        if(tenantId != null){
            return new DefaultParticipant(tenantId,
                                          "admin",
                                          Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                 ParticipantConstants.PARTICIPANT_TYPE_USER),
                                          List.of("ADMIN"));
        }else{
            return participant;
        }
    }
}
