import {Continuum, type IServiceProxy} from '@mindignited/continuum-client'

export class SessionMetadata {
    public sessionId!: string
    public replyToId!: string 
    constructor(sessionId: string, replyToId: string) {
        this.sessionId = sessionId
        this.replyToId = replyToId
    }
}

export interface ISessionUpgradeService {

    upgradeSession(upgradeId: string, sessionMetadata: SessionMetadata): Promise<void>

}

export class SessionUpgradeService implements ISessionUpgradeService {

    protected serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Continuum.serviceProxy('continuum.cli.SessionUpgradeService')
    }

    public async upgradeSession(upgradeId: string, sessionMetadata: SessionMetadata): Promise<void> {
        await this.serviceProxy.invoke('upgradeSession', [sessionMetadata], upgradeId)
    }
}


export const SESSION_UPGRADE_SERVICE: ISessionUpgradeService = new SessionUpgradeService()
