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

package org.kinotic.continuum.core.api.security;

import org.kinotic.continuum.api.security.DefaultParticipant;
import org.kinotic.continuum.api.security.Participant;

import java.util.concurrent.CompletableFuture;

/**
 * Provides an abstraction for creating and accessing {@link Session}'s
 *
 * Created by navid on 1/23/20
 */
public interface SessionManager {

    /**
     * Create a new {@link Session} for the given {@link DefaultParticipant}
     * @param participant the {@link DefaultParticipant} to create the {@link Session} for
     * @param replyToId that was provided by the client
     * @return a {@link CompletableFuture} containing the new {@link Session} or an error if the {@link Session} cannot be created
     */
    CompletableFuture<Session> create(Participant participant, String replyToId);

    /**
     * Removes the {@link Session} from the internal known sessions.
     * @param sessionId the id of the {@link Session} to remove
     * @return a {@link CompletableFuture} containing the result of the removal operation.
     *         True if there was a session for the given sessionId or false if there was no session for the id.
     */
    CompletableFuture<Boolean> removeSession(String sessionId);

    /**
     * Finds a previously created {@link Session} by the sessionId
     * @param sessionId to find the {@link Session} for
     * @return a {@link CompletableFuture} containing the existing {@link Session} or an error if the {@link Session} cannot be found
     */
    CompletableFuture<Session> findSession(String sessionId);

}
