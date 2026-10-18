import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { LoginProviderSummary } from '@/api/model/security/LoginProviderSummary'
import type { Organization } from '@/api/model/Organization'
import type { SignInMethod } from '@/api/model/security/SignInMethod'
import type { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * The signed-in account's own details, read and edited from the web app's account settings: the profile, the
 * ways the account signs in, and the organizations it may switch between. Every operation addresses the
 * calling account.
 */
export interface IProfileService {

    findMyProfile(): Promise<UserParticipantIdentity>

    updateDisplayName(displayName: string): Promise<UserParticipantIdentity>

    /** The ways the account signs in: its password, when it has one, and every provider identity bound to it. */
    findMySignInMethods(): Promise<SignInMethod[]>

    /**
     * The configurations the account may connect as further sign-in methods and has not yet: the platform's
     * registrations and the own configurations of the organizations the account may sign in to. Connecting one
     * is a form POST to `/api/auth/account/methods/start/<configurationId>`.
     */
    findConnectableProviders(): Promise<LoginProviderSummary[]>

    /**
     * Sets the account's password. An account that has one gives its current password; one that signs in by
     * a provider alone gives null and gains a password as a second method.
     */
    setPassword(currentPassword: string | null, newPassword: string): Promise<void>

    /** Removes a provider identity from the account's sign-in methods; the last method cannot be removed. */
    removeSignInMethod(methodId: string): Promise<void>

    /** The organizations the account is a member of and may sign in to, for switching between them. */
    findMyOrganizations(): Promise<Organization[]>

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

    public findMyOrganizations(): Promise<Organization[]> {
        return this.serviceProxy.invoke('findMyOrganizations', [])
    }
}
