import type {IParticipant} from '@kinotic-ai/core'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against an Application, carrying APPLICATION-scope authority over
 * that Application's own resources and end-user data. {@link organizationId} is the id of the
 * Organization that owns the Application; it identifies the owning org for data ownership and
 * routing of the Application's entity definitions and end-user data, and is never null.
 * Mirrors the server {@code ApplicationParticipant}.
 */
export interface IApplicationParticipant extends IParticipant {
    type: ParticipantType.APPLICATION;

    /**
     * The id of the Organization that owns the Application this participant is authenticated
     * under; never null.
     */
    organizationId: string;

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
