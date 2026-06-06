import type {IOrganizationParticipant} from './IOrganizationParticipant'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against an Application. Extends {@link IOrganizationParticipant}
 * because every Application belongs to an Organization, so an application-scoped session
 * intrinsically carries the owning Organization's id as well. Mirrors the server
 * {@code ApplicationParticipant}.
 */
export interface IApplicationParticipant extends IOrganizationParticipant {
    type: ParticipantType.APPLICATION;

    /**
     * The id of the Application this participant is authenticated under; never null.
     */
    applicationId: string;

    /**
     * The tenant slice of the Application's end-user data this participant belongs to, or null
     * when the Application is not multi-tenant.
     */
    tenantId?: string | null;
}
