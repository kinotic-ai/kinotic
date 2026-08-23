import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * The signed-in user's own account details, read and edited from the web app's account
 * settings. Every operation addresses the calling user.
 */
export interface IProfileService {

    /** Returns the calling user's identity. */
    findMyProfile(): Promise<UserParticipantIdentity>

    /**
     * Sets the calling user's display name, the name shown wherever they appear in the
     * platform, and returns the saved identity.
     */
    updateDisplayName(displayName: string): Promise<UserParticipantIdentity>

}

export class ProfileService implements IProfileService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.os.api.services.security.ProfileService`)
    }

    public findMyProfile(): Promise<UserParticipantIdentity> {
        return this.serviceProxy.invoke('findMyProfile', [])
    }

    public updateDisplayName(displayName: string): Promise<UserParticipantIdentity> {
        return this.serviceProxy.invoke('updateDisplayName', [displayName])
    }
}
