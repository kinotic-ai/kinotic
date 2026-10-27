import { type ISessionState, SessionState } from '@kinotic-ai/frontend-common'
import { isApplicationParticipant, isOrganizationParticipant } from '@kinotic-ai/management-api'
import { reactive } from 'vue'
import { rememberOrganization } from '@/pages/login/organizationLogin'

export interface IUserState extends ISessionState {

    /**
     * Returns the organization id of the authenticated participant. This client admits only
     * organization-scoped participants; it throws for application- and system-scoped participants.
     */
    getOrganizationId(): string
}

export class UserState extends SessionState implements IUserState {

    /**
     * Opens the realtime connection as {@link SessionState.login} does, and remembers the participant's
     * organization as the one this browser last signed in to, whichever sign-in method established the session.
     */
    public override async login(): Promise<void> {
        await super.login()
        const organizationId = this.organizationIdOrNull()
        if (organizationId !== null) {
            rememberOrganization(organizationId)
        }
    }

    public getOrganizationId(): string {
        const ret = this.organizationIdOrNull()
        if (ret === null) {
            throw new Error('No organization id available — this client requires an organization-scoped session')
        }
        return ret
    }

    private organizationIdOrNull(): string | null {
        const participant = this.connectedInfo?.participant
        // This client admits organization administrators only. An application-scoped participant
        // also carries an organizationId, so it is excluded explicitly rather than by absence.
        let ret: string | null = null
        if (participant && isOrganizationParticipant(participant) && !isApplicationParticipant(participant)) {
            ret = participant.organizationId
        }
        return ret
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
