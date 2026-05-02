package org.kinotic.gateway.internal.api;

import org.kinotic.core.api.security.DefaultParticipant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 7/10/23.
 */
public class CliSecurityService implements SecurityService {

    private final SecurityService delegate;

    public CliSecurityService(SecurityService delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        String login = authenticationInfo.get("login");
        if(login != null && login.equals(ParticipantConstants.CLI_PARTICIPANT_ID)){
            return CompletableFuture.completedFuture(new DefaultParticipant(ParticipantConstants.CLI_PARTICIPANT_ID,
                                                                            Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                   ParticipantConstants.PARTICIPANT_TYPE_CLI),
                                                                            List.of("ANONYMOUS")));
        }else {
            return delegate.authenticate(authenticationInfo);
        }
    }
}
