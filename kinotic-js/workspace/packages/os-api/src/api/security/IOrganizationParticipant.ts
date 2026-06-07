import type {IParticipant} from '@kinotic-ai/core'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against an Organization, carrying ORGANIZATION-scope authority over
 * that Organization's resources. {@link organizationId} is the id of the owning Organization and
 * is never null. Mirrors the server {@code OrganizationParticipant}.
 */
export interface IOrganizationParticipant extends IParticipant {
    type: ParticipantType.ORGANIZATION;

    /**
     * The id of the Organization this participant is authenticated under; never null.
     */
    organizationId: string;
}
