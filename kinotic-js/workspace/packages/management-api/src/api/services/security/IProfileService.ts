import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { LoginProviderSummary } from '@/api/model/security/LoginProviderSummary'
import type { SignInMethod } from '@/api/model/security/SignInMethod'
import type { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * The signed-in user's own details, read and edited from the web app's profile settings: the profile and the
 * ways the user signs in. Every operation addresses the calling user: a system user from the system console, an
 * organization's user from the portal.
 */
export interface IProfileService {

    findMyProfile(): Promise<UserParticipantIdentity>

    updateDisplayName(displayName: string): Promise<UserParticipantIdentity>

    /**
     * The ways the user signs in: its password, when it has one and its login takes a password, and every provider
     * identity bound to it.
     */
    findMySignInMethods(): Promise<SignInMethod[]>

    /**
     * The configurations an organization's user may connect as further sign-in methods and has not yet: those its
     * organization's login offers, the system's registrations and the organization's own; none for a system user,
     * which signs in with a password alone. Connecting one
     * is a form POST to `/api/auth/org/methods/start/<configurationId>`.
     */
    findConnectableProviders(): Promise<LoginProviderSummary[]>

    /**
     * Sets the user's password. A user that has one gives its current password; one that signs in by
     * a provider alone gives null and gains a password as a second method. Refused for an organization's user while
     * its organization's login takes no password.
     */
    setPassword(currentPassword: string | null, newPassword: string): Promise<void>

    /**
     * Removes a provider identity from the user's sign-in methods; the last method that still signs the user in
     * (its password while its login takes one, or a configuration its organization's login offers) cannot be removed.
     */
    removeSignInMethod(methodId: string): Promise<void>

}

export class ProfileService implements IProfileService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.ProfileService`)
    }

    public findMyProfile(): Promise<UserParticipantIdentity> {
        return this.serviceProxy.invoke('findMyProfile', [])
    }

    public updateDisplayName(displayName: string): Promise<UserParticipantIdentity> {
        return this.serviceProxy.invoke('updateDisplayName', [displayName])
    }

    public findMySignInMethods(): Promise<SignInMethod[]> {
        return this.serviceProxy.invoke('findMySignInMethods', [])
    }

    public findConnectableProviders(): Promise<LoginProviderSummary[]> {
        return this.serviceProxy.invoke('findConnectableProviders', [])
    }

    public setPassword(currentPassword: string | null, newPassword: string): Promise<void> {
        return this.serviceProxy.invoke('setPassword', [currentPassword, newPassword])
    }

    public removeSignInMethod(methodId: string): Promise<void> {
        return this.serviceProxy.invoke('removeSignInMethod', [methodId])
    }
}
