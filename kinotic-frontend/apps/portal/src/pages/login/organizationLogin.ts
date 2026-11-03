import { apiUrl } from '@kinotic-ai/frontend-common'

/** A sign-in provider an organization's login offers, as `GET /api/auth/org/login/:orgId/providers` lists it. */
export interface LoginProvider {
    id: string
    /** PASSWORD for an email and a password, OIDC for a provider. */
    type: 'PASSWORD' | 'OIDC'
    name: string
    provider: string | null
}

/** The sign-in providers the login of organization `organizationId` offers, which also accept its invitations. */
export async function findLoginProviders(organizationId: string): Promise<LoginProvider[]> {
    const res = await fetch(apiUrl(`/api/auth/org/login/${encodeURIComponent(organizationId)}/providers`),
                            { credentials: 'same-origin' })
    if (!res.ok) {
        throw new Error(`The organization's sign-in providers could not be loaded (${res.status})`)
    }
    return await res.json()
}

/** localStorage key of the id of the organization this browser last signed in to. */
const LAST_ORGANIZATION_KEY = 'kinotic.lastOrganization'

/** The id of the organization this browser last signed in to, or null when none is remembered. */
export function rememberedOrganization(): string | null {
    let ret: string | null = null
    try {
        ret = localStorage.getItem(LAST_ORGANIZATION_KEY) || null
    } catch {
        // blocked storage reads as a fresh browser
    }
    return ret
}

/** Remembers the organization this browser signed in to, for its next visit to the landing page. */
export function rememberOrganization(organizationId: string): void {
    try {
        localStorage.setItem(LAST_ORGANIZATION_KEY, organizationId)
    } catch {
        // blocked storage leaves the landing page asking for the organization
    }
}

/** Forgets the organization this browser last signed in to. */
export function forgetOrganization(): void {
    try {
        localStorage.removeItem(LAST_ORGANIZATION_KEY)
    } catch {
        // blocked storage leaves the landing page asking for the organization
    }
}

/**
 * Key, in the history state of a navigation to the landing page, of the remembered organization the
 * organization page found no longer exists. The landing page prefills its organization field with it.
 */
export const UNKNOWN_ORGANIZATION_STATE = 'unknownOrganization'

/** The portal path of an organization's login page. */
export function organizationLoginPath(organizationId: string): string {
    return '/login/' + encodeURIComponent(organizationId)
}

/**
 * The organization id named by what a person typed: the id itself, the organization's host
 * (`acme.kinotic.io`), whose first label is the id, or one of its sites' URLs
 * (`orders--web.acme.kinotic.io`), whose second label is. Empty when nothing was typed.
 */
export function organizationSlug(typed: string): string {
    let ret = typed.trim().toLowerCase()
    ret = ret.replace(/^[a-z]+:\/\//, '')
    const labels = (ret.split('/')[0] ?? '').split('.')
    // a site's label joins its names with --, which an organization id never contains
    const label = labels[0]?.includes('--') ? labels[1] : labels[0]
    return label ?? ''
}
