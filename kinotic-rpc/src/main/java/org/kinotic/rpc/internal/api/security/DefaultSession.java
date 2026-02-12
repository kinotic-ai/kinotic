

package org.kinotic.rpc.internal.api.security;

import org.kinotic.rpc.api.security.Participant;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Date;
import java.util.List;

/**
 *
 * Created by Navid Mitchell on 6/2/20
 */
public class DefaultSession extends AbstractSession {


    public DefaultSession(DefaultSessionManager sessionManager,
                          Participant participant,
                          String sessionId,
                          String replyToId,
                          PathContainer.Options parseOptions,
                          List<PathPattern> sendPathPatterns,
                          List<PathPattern> subscribePathPatterns) {
        super(sessionManager, participant, sessionId, replyToId, parseOptions, sendPathPatterns, subscribePathPatterns);
    }

    @Override
    public void touch() {
        lastUsedDate = new Date();
    }
}
