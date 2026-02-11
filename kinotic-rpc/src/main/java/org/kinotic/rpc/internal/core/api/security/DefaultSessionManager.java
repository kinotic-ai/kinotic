/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.rpc.internal.core.api.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.prng.PRNG;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.api.security.ParticipantConstants;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.core.api.security.Session;
import org.kinotic.continuum.core.api.security.SessionManager;
import org.kinotic.rpc.internal.config.IgniteCacheConstants;
import org.kinotic.rpc.internal.utils.IgniteUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by navid on 1/23/20
 */
@Component
public class DefaultSessionManager implements SessionManager {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final PRNG random;
    private final PathPatternParser parser;
    private final IgniteCache<String, DefaultSessionMetadata> sessionCache;
    private final LoadingCache<String, PathPattern> pathPatternCache;

    public DefaultSessionManager(Vertx vertx,
                                 @Autowired(required = false) Ignite ignite) {

        this.random = new PRNG(vertx);
        this.parser = new PathPatternParser();
        this.parser.setPathOptions(PathContainer.Options.MESSAGE_ROUTE);

        // Will be null when running some tests
        if(ignite !=  null){
            sessionCache = ignite.cache(IgniteCacheConstants.SESSION_CACHE_NAME);
        }else{
            sessionCache = null;
        }

        pathPatternCache = Caffeine.newBuilder()
                                   .expireAfterAccess(Duration.ofHours(12))
                                   .build(parser::parse);
    }

    @Override
    public CompletableFuture<Session> create(Participant participant, String replyToId) {

        String sessionId = generateId();

        ParticipantPathPatterns participantPathPatterns = new ParticipantPathPatterns(participant, replyToId);

        if(sessionCache != null){
            IgniteSession igniteSession = new IgniteSession(this,
                                                            participant,
                                                            sessionId,
                                                            replyToId,
                                                            parser.getPathOptions(),
                                                            participantPathPatterns.sendPatterns,
                                                            participantPathPatterns.subscriptionPatterns,
                                                            sessionCache);

            DefaultSessionMetadata sessionMetadata = new DefaultSessionMetadata()
                    .setSessionId(sessionId)
                    .setReplyToId(replyToId)
                    .setParticipant(participant)
                    .setLastUsedDate(new Date());


            return IgniteUtil.futureToCompletableFuture(sessionCache.putAsync(sessionId, sessionMetadata))
                             .thenApply(put -> igniteSession);

        }else{
            return CompletableFuture.completedFuture(new DefaultSession(this,
                                                                        participant,
                                                                        sessionId,
                                                                        replyToId,
                                                                        parser.getPathOptions(),
                                                                        participantPathPatterns.sendPatterns,
                                                                        participantPathPatterns.subscriptionPatterns));
        }
    }

    @Override
    public CompletableFuture<Boolean> removeSession(String sessionId) {
        return IgniteUtil.futureToCompletableFuture(sessionCache.removeAsync(sessionId));
    }

    @Override
    public CompletableFuture<Session> findSession(String sessionId) {
        CompletableFuture<Session> ret;
        if(sessionCache != null){
            ret = IgniteUtil.futureToCompletableFuture(sessionCache.getAsync(sessionId))
                            .thenCompose(defaultSessionMetadata -> {
                                if (defaultSessionMetadata != null) {
                                    Participant participant = defaultSessionMetadata.getParticipant();
                                    ParticipantPathPatterns participantPathPatterns = new ParticipantPathPatterns(participant,
                                                                                                                  defaultSessionMetadata.getReplyToId());
                                    Session session = new IgniteSession(this,
                                                                        participant,
                                                                        defaultSessionMetadata.getSessionId(),
                                                                        defaultSessionMetadata.getReplyToId(),
                                                                        parser.getPathOptions(),
                                                                        participantPathPatterns.sendPatterns,
                                                                        participantPathPatterns.subscriptionPatterns,
                                                                        sessionCache);
                                    return CompletableFuture.completedFuture(session);
                                } else {
                                    return CompletableFuture.failedFuture(new IllegalArgumentException("No session can be found for the given id: " + sessionId));
                                }
                            });
        }else{
            ret = CompletableFuture.failedFuture(new IllegalStateException("Find Session can only be used when Ignite is enabled"));
        }
        return ret;
    }

    public PathPattern getPathPattern(String pattern) {
        return pathPatternCache.get(pattern);
    }

    private class ParticipantPathPatterns {
        List<PathPattern> sendPatterns = new LinkedList<>();
        List<PathPattern> subscriptionPatterns = new LinkedList<>();

        public ParticipantPathPatterns(Participant participant, String replyToId) {
            // The CLI is allowed to log in anonymously and receive events scoped to its identity but cannot send events or service requests
            if(!participant.getId().equals(ParticipantConstants.CLI_PARTICIPANT_ID)) {

                // FIXME: this is a hack for now, we should be using the participants roles
                List<String> allowedSendPatterns = List.of(EventConstants.SERVICE_DESTINATION_SCHEME + "://*.**",
                                                           EventConstants.STREAM_DESTINATION_SCHEME + "://*.**");

                // Add send patterns
                for (String path : allowedSendPatterns) {
                    sendPatterns.add(getPathPattern(path));
                }

                List<String> allowedSubscriptionPatterns = List.of(EventConstants.SERVICE_DESTINATION_SCHEME + "://*.**",
                                                                   EventConstants.STREAM_DESTINATION_SCHEME + "://*.**");

                for(String path: allowedSubscriptionPatterns){
                    subscriptionPatterns.add(getPathPattern(path));
                }

            }

            // clients can subscribe to any service that is scoped to their identity, including the CLI
            subscriptionPatterns.add(parser.parse(EventConstants.SERVICE_DESTINATION_SCHEME + "://"
                                                          + replyToId
                                                          + ":*@*.**"));
        }
    }

    private String generateId() {
        // Default length for a session id is 16 bytes, More info: https://www.owasp.org/index.php/Session_Management_Cheat_Sheet
        final byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        final char[] hex = new char[16 * 2];
        for (int j = 0; j < 16; j++) {
            int v = bytes[j] & 0xFF;
            hex[j * 2] = HEX[v >>> 4];
            hex[j * 2 + 1] = HEX[v & 0x0F];
        }

        return new String(hex);
    }

}
