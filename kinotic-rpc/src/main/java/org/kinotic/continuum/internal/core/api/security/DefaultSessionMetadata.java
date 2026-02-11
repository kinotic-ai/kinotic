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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.core.api.security.SessionMetadata;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * Created by Navid Mitchell on 6/2/20
 */
public class DefaultSessionMetadata implements Serializable, SessionMetadata {

    @JsonProperty
    @QuerySqlField(index = true)
    private String sessionId;

    @JsonProperty
    private String replyToId;

    @JsonProperty
    private Participant participant;

    @JsonProperty
    @QuerySqlField
    private Date lastUsedDate;

    public DefaultSessionMetadata() {
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    public DefaultSessionMetadata setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    @Override
    public String getReplyToId() {
        return replyToId;
    }

    public DefaultSessionMetadata setReplyToId(String replyToId) {
        this.replyToId = replyToId;
        return this;
    }

    @Override
    public Participant getParticipant() {
        return participant;
    }

    public DefaultSessionMetadata setParticipant(Participant participant) {
        this.participant = participant;
        return this;
    }

    @Override
    public Date getLastUsedDate() {
        return lastUsedDate;
    }

    public DefaultSessionMetadata setLastUsedDate(Date lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
        return this;
    }

}
