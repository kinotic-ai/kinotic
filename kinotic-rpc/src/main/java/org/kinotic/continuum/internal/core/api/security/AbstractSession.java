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

package org.kinotic.continuum.internal.core.api.security;

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.core.api.security.Session;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by navid on 1/23/20
 */
public abstract class AbstractSession implements Session {

    private static final Logger log = LoggerFactory.getLogger(AbstractSession.class);

    private static final int MAX_TEMPORARY_PATTERNS = 1000;

    private final DefaultSessionManager sessionManager;
    private final Participant participant;
    private final String sessionId;
    private final String replyToId;
    private final PathContainer.Options parseOptions;
    private final List<PathPattern> sendPathPatterns;
    private final List<PathPattern> subscribePathPatterns;
    private final LinkedList<PathPattern> temporarySendPathPatterns = new LinkedList<>();

    protected Date lastUsedDate;

    public AbstractSession(DefaultSessionManager sessionManager,
                           Participant participant,
                           String sessionId,
                           String replyToId,
                           PathContainer.Options parseOptions,
                           List<PathPattern> sendPathPatterns,
                           List<PathPattern> subscribePathPatterns) {
        this.sessionManager = sessionManager;
        this.participant = participant;
        this.sessionId = sessionId;
        this.replyToId = replyToId;
        this.parseOptions = parseOptions;
        this.sendPathPatterns = sendPathPatterns;
        this.subscribePathPatterns = subscribePathPatterns;
        this.lastUsedDate = new Date();
    }

    @Override
    public Participant participant(){
        return participant;
    }

    @Override
    public String sessionId(){
        return sessionId;
    }

    @Override
    public String replyToId() {
        return replyToId;
    }

    @Override
    public Date lastUsedDate() {
        return lastUsedDate;
    }

    @Override
    public void addTemporarySendAllowed(String criPattern) {
        if(temporarySendPathPatterns.size() == MAX_TEMPORARY_PATTERNS){
            temporarySendPathPatterns.removeFirst();
            // Just in case this actually happens in production
            log.warn("Reached Max Temporary patterns some messages may be dropped");
        }
        temporarySendPathPatterns.add(sessionManager.getPathPattern(criPattern));
    }

    @Override
    public boolean sendAllowed(CRI cri){
        Validate.notNull(cri, "The CRI must not be null");
        int result = -1;

        // check one time paths
        if(!temporarySendPathPatterns.isEmpty()){
            result = checkMatches(cri.raw(), temporarySendPathPatterns);
            // temporary patterns can only match once
            if(result != -1){
                temporarySendPathPatterns.remove(result);
            }
        }

        // Check configured paths
        if(result == -1){
            result = checkMatches(cri.raw(), sendPathPatterns);
        }
        return result != -1;
    }

    @Override
    public boolean subscribeAllowed(CRI cri){
        Validate.notNull(cri, "The CRI must not be null");
        return checkMatches(cri.raw(), subscribePathPatterns) != -1;
    }

    private int checkMatches(String cri, List<PathPattern> patterns){
        int ret = -1;
        PathContainer pathContainer = PathContainer.parsePath(cri, parseOptions);
        for(int i = 0; i < patterns.size(); i++){
            if(patterns.get(i).matches(pathContainer)){
                ret = i;
                break;
            }
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof Session that)) return false;

        return new EqualsBuilder().append(participant, that.participant())
                                  .append(sessionId, that.sessionId())
                                  .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(participant).append(sessionId).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("sessionId", sessionId)
                .append("lastUsedDate", lastUsedDate)
                .append("participant", participant)
                .toString();
    }
}
