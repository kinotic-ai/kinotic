import type {Identifiable} from '@/api/crud/Identifiable'

/**
 * Identifying information about a logged-in participant on the RPC layer.
 *
 * This is the base shape every participant shares, regardless of scope, and is what RPC-only
 * consumers work with directly. The Kinotic OS contract layers scope-typed participants on top
 * of it — see the participant types in {@code @kinotic-ai/os-api}.
 *
 * Mirrors the server {@code org.kinotic.core.api.security.Participant}.
 *
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
export interface IParticipant extends Identifiable<string> {

    /**
     * The identity of the participant.
     */
    id: string;

    /**
     * Key/value pairs carrying additional information about the participant.
     */
    metadata: Record<string, string>;

    /**
     * Roles used to authorize the participant to perform actions.
     */
    roles: string[];
}
