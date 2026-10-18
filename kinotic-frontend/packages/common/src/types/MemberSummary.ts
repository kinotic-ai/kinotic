import type { DescriptiveIdentifiable } from './DescriptiveIdentifiable'

/** One person in a members or users list: a signed-up user, or an invitation still pending. */
export interface MemberSummary extends DescriptiveIdentifiable {
    id: string
    email: string
    displayName: string | null
    status: 'Invited' | 'Active' | 'Disabled'
    /** When the person joined the scope, or when the invitation was sent. */
    created: string | null
    invite?: boolean
}
