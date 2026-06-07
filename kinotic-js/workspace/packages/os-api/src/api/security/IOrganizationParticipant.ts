import type {IParticipant} from '@kinotic-ai/core'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against an Organization. {@link organizationId} is the id of the
 * owning Organization and is never null. Only org-scope sessions are this type; an
 * application-scoped participant is not an {@link IOrganizationParticipant}. Mirrors the server
 * {@code OrganizationParticipant}.
 */
export interface IOrganizationParticipant extends IParticipant {
    type: ParticipantType.ORGANIZATION;

    /**
     * The id of the Organization this participant is authenticated under; never null.
     */
    organizationId: string;
}
