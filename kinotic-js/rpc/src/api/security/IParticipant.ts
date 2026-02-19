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

import {Identifiable} from '@/api/Identifiable'

/**
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
export interface IParticipant extends Identifiable<string> {
    /**
     * The identity of the participant
     *
     * @return the identity of the participant
     */
    id: string;

    /**
     * The tenant that the participant belongs to
     *
     * @return the tenant or null if not using multi-tenancy
     */
    tenantId?: string | null;

    /**
     * Metadata is a map of key value pairs that can be used to store additional information about a participant
     *
     * @return a map of key value pairs
     */
    metadata: Map<string, string>;

    /**
     * Roles are a list of strings that can be used to authorize a participant to perform certain actions
     *
     * @return a list of roles
     */
    roles: string[];
}
