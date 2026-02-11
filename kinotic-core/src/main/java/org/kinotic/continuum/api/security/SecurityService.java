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

package org.kinotic.continuum.api.security;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@link SecurityService} provides core security functionality.
 *
 *
 * Created by navid on 2019-05-01.
 */
public interface SecurityService {

    /**
     * Check if a given participant can authenticate
     * @param authenticationInfo a {@link Map} containing the authentication information
     * @return a {@link CompletableFuture} completing with a {@link DefaultParticipant} if authentication was successful or an error if authentication failed
     *         WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
     */
    CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo);

}
