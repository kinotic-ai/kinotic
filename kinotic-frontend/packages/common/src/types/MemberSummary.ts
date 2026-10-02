import type { DescriptiveIdentifiable } from './DescriptiveIdentifiable'

/** One person in a members or users list: a signed-up user, or an invitation still pending. */
export interface MemberSummary extends DescriptiveIdentifiable {
    id: string
    email: string
    displayName: string | null
    status: 'Invited' | 'Active' | 'Disabled'
    /** How the person signs in, OIDC or LOCAL; null for an invitation. */
    authType: string | null
    created: number | null
    invite?: boolean
}
