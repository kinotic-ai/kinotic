import { Kinotic } from '@kinotic-ai/core'
import type { UserParticipantIdentity } from '@kinotic-ai/management-api'
import { avatarInitials } from '@kinotic-ai/frontend-common'
import { reactive } from 'vue'

/**
 * The signed-in user's own profile, shared by everything that shows who is signed in. Saving a
 * display name through this state updates every consumer.
 */
export interface IProfileState {
    profile: UserParticipantIdentity | null

    /** Up to two initials standing in for an avatar image; empty until the profile loads. */
    readonly initials: string

    /** Loads the profile once. Concurrent callers share the one request. */
    load(): Promise<void>

    /** Saves the display name and updates {@link profile} with what the server stored. */
    updateDisplayName(displayName: string): Promise<void>
}

export class ProfileState implements IProfileState {

    public profile: UserParticipantIdentity | null = null

    private inFlight: Promise<void> | null = null

    public get initials(): string {
        return avatarInitials(this.profile?.displayName, this.profile?.email)
    }

    public load(): Promise<void> {
        if (this.inFlight === null) {
            // the header and the profile page mount together, so they share one request;
            // clearing it on failure lets the next mount retry
            this.inFlight = Kinotic.profile.findMyProfile()
                                   .then(profile => { this.profile = profile })
                                   .catch(error => { this.inFlight = null; throw error })
        }
        return this.inFlight
    }

    public async updateDisplayName(displayName: string): Promise<void> {
        this.profile = await Kinotic.profile.updateDisplayName(displayName)
    }
}

export const PROFILE_STATE: IProfileState = reactive(new ProfileState())
