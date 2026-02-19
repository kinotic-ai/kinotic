

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
