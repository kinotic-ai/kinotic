

package org.kinotic.rpc.internal.api.security;

import org.kinotic.rpc.api.security.Participant;
import org.apache.ignite.IgniteCache;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Date;
import java.util.List;

/**
 *
 * Created by Navid Mitchell on 6/2/20
 */
public class IgniteSession extends AbstractSession {

    private final IgniteCache<String, DefaultSessionMetadata> sessionCache;

    public IgniteSession(DefaultSessionManager sessionManager,
                         Participant participant,
                         String sessionId,
                         String replyToId,
                         PathContainer.Options parseOptions,
                         List<PathPattern> sendPathPatterns,
                         List<PathPattern> subscribePathPatterns,
                         IgniteCache<String, DefaultSessionMetadata> sessionCache) {
        super(sessionManager, participant, sessionId, replyToId, parseOptions, sendPathPatterns, subscribePathPatterns);
        this.sessionCache = sessionCache;
    }

    @Override
    public void touch() {
        lastUsedDate = new Date();
        DefaultSessionMetadata meta = sessionCache.get(sessionId());
        meta.setLastUsedDate(lastUsedDate);
        sessionCache.put(sessionId(), meta);
    }

}
