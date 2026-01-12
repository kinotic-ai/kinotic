import { IServiceProxy, IServiceRegistry } from '@kinotic-foundation/continuum-js'
import { inject, injectable, container } from 'inversify-props'
import { Observable } from 'rxjs'
import { StreamData } from '@kinotic-foundation/continuum-js'

export class SessionMetadata {

    public sessionId: string = ''

    public participantIdentity: string = ''

    public participantType: string = ''

    public lastUsedDate: string = ''

}

export interface ISessionInformationService {

    countActiveSessionsContinuous(): Observable<number>

    listActiveSessionsContinuous(): Observable<StreamData<string, SessionMetadata>>

}

@injectable()
export class SessionInformationService implements ISessionInformationService {

    protected serviceProxy: IServiceProxy

    constructor(@inject() serviceRegistry: IServiceRegistry) {
        this.serviceProxy = serviceRegistry.serviceProxy('org.mindignited.continuum.gateway.api.security.SessionInformationService')
    }

    public countActiveSessionsContinuous(): Observable<number> {
        return this.serviceProxy.invokeStream('countActiveSessionsContinuous') as Observable<number>
    }

    public listActiveSessionsContinuous(): Observable<StreamData<string, SessionMetadata>> {
        return this.serviceProxy.invokeStream('listActiveSessionsContinuous') as Observable<StreamData<string, SessionMetadata>>
    }

}

container.addSingleton<ISessionInformationService>(SessionInformationService)
