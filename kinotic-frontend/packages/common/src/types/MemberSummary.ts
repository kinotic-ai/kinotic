import type { DescriptiveIdentifiable } from './DescriptiveIdentifiable'

/** One person in a members or users list, pending until they first sign in. */
export interface MemberSummary extends DescriptiveIdentifiable {
    id: string
    email: string
    displayName: string | null
    status: 'Pending' | 'Active' | 'Disabled'
    /** When the person was added to the scope. */
    created: string | null
}
