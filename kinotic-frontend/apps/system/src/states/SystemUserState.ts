import { type ISessionState, SessionState } from '@kinotic-ai/frontend-common'
import { isSystemParticipant } from '@kinotic-ai/os-api'
import { reactive } from 'vue'

export interface ISystemUserState extends ISessionState {
}

export class SystemUserState extends SessionState implements ISystemUserState {

    public override async login(): Promise<void> {
        await super.login()
        // This client admits platform operators only. A session established by any other
        // login surface is destroyed rather than admitted with narrower scope.
        const participant = this.connectedInfo?.participant
        if (!participant || !isSystemParticipant(participant)) {
            await this.logout()
            throw new Error('This client requires a system-scoped session')
        }
    }
}

export const SYSTEM_USER_STATE: ISystemUserState = reactive(new SystemUserState())
