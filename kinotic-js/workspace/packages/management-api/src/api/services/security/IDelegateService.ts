import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, IterablePage, Page, Pageable } from '@kinotic-ai/core'
import { FunctionalIterablePage } from '@kinotic-ai/core'
import type { DelegateSession } from '@/api/model/security/DelegateSession'
import type { DelegatingParticipantIdentity } from '@/api/model/security/DelegatingParticipantIdentity'

/**
 * The signed-in user's view of the clients authorized to act on their behalf — CLI installs
 * and MCP hosts — with the sessions each one holds, and revocation of a single session or a
 * whole client. Every operation is bound to the calling user.
 */
export interface IDelegateService {

    /**
     * Lists the delegates authorized on the calling user's behalf, revoked ones included
     * (disabled entries show as revoked until re-approved).
     */
    findMyDelegates(pageable: Pageable): Promise<IterablePage<DelegatingParticipantIdentity>>

    /**
     * Lists the live sessions of one of the calling user's delegates — one entry per
     * refresh-token family, labeled when the client supplied a device name.
     */
    findSessions(delegateId: string): Promise<DelegateSession[]>

    /**
     * Ends a single session of one of the calling user's delegates. Its tokens can no longer
     * be refreshed; an unexpired access token runs out on its own short TTL.
     */
    revokeSession(delegateId: string, familyId: string): Promise<void>

    /**
     * Revokes one of the calling user's delegates entirely: the client is cut off on its next
     * request and every session it holds is ended. A later re-approval restores access.
     */
    revokeDelegate(delegateId: string): Promise<void>

}

export class DelegateService implements IDelegateService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.DelegateService`)
    }

    public async findMyDelegates(pageable: Pageable): Promise<IterablePage<DelegatingParticipantIdentity>> {
        const page: Page<DelegatingParticipantIdentity> = await this.serviceProxy.invoke('findMyDelegates', [pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMyDelegates', [next]))
    }

    public findSessions(delegateId: string): Promise<DelegateSession[]> {
        return this.serviceProxy.invoke('findSessions', [delegateId])
    }

    public revokeSession(delegateId: string, familyId: string): Promise<void> {
        return this.serviceProxy.invoke('revokeSession', [delegateId, familyId])
    }

    public revokeDelegate(delegateId: string): Promise<void> {
        return this.serviceProxy.invoke('revokeDelegate', [delegateId])
    }
}
