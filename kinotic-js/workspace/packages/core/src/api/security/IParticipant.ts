import type {Identifiable} from '@/api/crud/Identifiable'

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
     * The scope layer this participant authenticated against.
     * Well-known values are "SYSTEM", "ORGANIZATION", and "APPLICATION",
     * but custom values are allowed for extensibility.
     */
    authScopeType?: string | null;

    /**
     * The identifier of the specific scope this participant belongs to.
     * For example, "kinotic" for system scope, an organization ID, or an application ID.
     * Together with {@link authScopeType}, uniquely identifies which user pool
     * this participant was authenticated from.
     */
    authScopeId?: string | null;

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
