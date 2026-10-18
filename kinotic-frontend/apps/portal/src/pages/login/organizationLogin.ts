/** localStorage key of the id of the organization this browser last signed in to. */
export const LAST_ORGANIZATION_KEY = 'kinotic.lastOrganization'

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
