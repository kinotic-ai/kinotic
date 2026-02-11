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

import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.core.api.event.CRI;

import java.util.Date;

/**
 *
 * Created by navid on 1/23/20
 */
public interface Session {

    Participant participant();

    String sessionId();

    String replyToId();

    Date lastUsedDate();

    /**
     * Updates the lastUsedDate to the current date and time
     */
    void touch();

    /**
     * Adds a criPattern that will allow a send to the given {@link CRI} one time
     * This is determined by a call to {@link Session#sendAllowed(CRI)} after the first call returning true the criPattern will not be allowed again
     * @param criPattern to add to the temporary allowed list
     */
    void addTemporarySendAllowed(String criPattern);

    boolean sendAllowed(CRI cri);

    boolean subscribeAllowed(CRI cri);

}
