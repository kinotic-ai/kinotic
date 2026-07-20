import { type ISessionState, SessionState } from '@kinotic-ai/frontend-common'
import { isApplicationParticipant, isOrganizationParticipant } from '@kinotic-ai/os-api'
import { reactive } from 'vue'

export interface IUserState extends ISessionState {

    /**
     * Returns the organization id of the authenticated participant. This client admits only
     * organization-scoped participants; it throws for application- and system-scoped participants.
     */
    getOrganizationId(): string
}

export class UserState extends SessionState implements IUserState {

    public getOrganizationId(): string {
        const participant = this.connectedInfo?.participant
        // This client admits organization administrators only. An application-scoped participant
        // also carries an organizationId, so it is excluded explicitly rather than by absence.
        if (!participant || !isOrganizationParticipant(participant) || isApplicationParticipant(participant)) {
            throw new Error('No organization id available — this client requires an organization-scoped session')
        }
        return participant.organizationId
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
