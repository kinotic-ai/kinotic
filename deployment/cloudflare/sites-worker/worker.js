/**
 * Serves every organization's published sites under the sites domain from the sites storage account, the
 * platform's own UIs from the same account at their hostnames in the platform zone, and sends an
 * organization's own host to the portal's login for that organization.
 *
 * A site is <app>--<ui>.<org>.<SITES_DOMAIN>, and a site addressed to one tenant of the application is
 * <tenant>--<app>--<ui>.<org>.<SITES_DOMAIN>; both are served from the application's site directory,
 * sites/<app>--<ui>.<org>.<SITES_DOMAIN>/ in the account, which the publish workload writes. A request that
 * names a file is served as that file; any other path is a route of the single-page application and is
 * served its index. The organization's own host, <org>.<SITES_DOMAIN>, redirects to <PORTAL_BASE_URL>/login/<org>.
 * The platform's own UIs, the portal and the console, are the hosts UI_HOSTS names, each served from sites/<host>/
 * the same way; they are in the platform zone, never under the sites domain, so no site is same-site with them.
 *
 * The Worker is bound to the site hosts only: the application API hosts, <org>--<app>.apps-api.<zone>, go to
 * the app server directly, so no API call or WebSocket passes through here.
 *
 * Bindings (environment variables set by terraform):
 *   SITES_DOMAIN        the domain every organization's sites are under, e.g. kinotic.io or dev-nm.kinotic.io
 *   SITES_STORAGE_URL   the blob endpoint of the sites account, e.g. https://stkinoticsites.blob.core.windows.net
 *   SITES_STORAGE_SAS   the query string of the read-only SAS on the sites container, a secret, which signs every read
 *   PORTAL_BASE_URL     where an organization's host sends the browser, e.g. https://portal.kinotic.ai
 *   UI_HOSTS            the platform's own UIs' hostnames, comma-separated, e.g. portal.kinotic.ai,console.kinotic.ai
 */

const LABEL_SEPARATOR = '--'
/**
 * The labels no organization id may take, which DomainUtil.validateOrganizationId refuses: the platform's own hosts
 * (RESERVED_ORGANIZATION_IDS, with DomainUtil.SYSTEM_ID) and xn, whose host labels would start with the DNS-reserved
 * xn--. A host of one of them is never a site, so the Worker refuses it even when a route sends it here.
 */
const RESERVED_LABELS = new Set(['www', 'api', 'apps-api', 'system-api', 'portal', 'console', 'mail', 'admin', 'login', 'status', 'docs',
                                 'find', 'social', 'sso', 'providers', 'choices', 'system', 'xn'])
const SITES_CONTAINER = 'sites'
const INDEX = 'index.html'
/** Hashed assets are published under this directory and never change, so the edge keeps them. */
const IMMUTABLE_PREFIX = '/assets/'
const IMMUTABLE_TTL_SECONDS = 31536000
/** The index and version.json change on every publish, so the edge keeps them only briefly. */
const MUTABLE_TTL_SECONDS = 60

/**
 * What a host under the sites domain names, or null when it is not a site or an organization host.
 *
 * @param {string} host the request's hostname, lowercase
 * @param {string} sitesDomain the sites domain, lowercase
 * @returns {{ organizationId: string, site: string | null, tenantId: string | null } | null} the organization,
 *          the site's hostname under the sites domain (null for the organization's own host), and the tenant
 *          the request is addressed to
 */
export function resolveHost(host, sitesDomain) {
    const suffix = '.' + sitesDomain
    if (!host.endsWith(suffix)) {
        return null
    }
    const labels = host.slice(0, -suffix.length).split('.')
    if (labels.some(label => label.length === 0) || RESERVED_LABELS.has(labels[labels.length - 1])) {
        return null
    }
    if (labels.length === 1) {
        return { organizationId: labels[0], site: null, tenantId: null }
    }
    if (labels.length !== 2) {
        return null
    }
    const names = labels[0].split(LABEL_SEPARATOR)
    if (names.length < 2 || names.length > 3 || names.some(name => name.length === 0)) {
        return null
    }
    const app = names.length - 2
    return {
        organizationId: labels[1],
        site: names[app] + LABEL_SEPARATOR + names[app + 1] + '.' + labels[1] + suffix,
        tenantId: names.length === 3 ? names[0] : null,
    }
}

/**
 * The file a request path is served: the file it names, or the site's index for a route of the
 * single-page application, which names no file.
 *
 * @param {string} pathname the request path
 * @returns {string} the file's path within the site, beginning with /
 */
export function resolveFile(pathname) {
    const last = pathname.slice(pathname.lastIndexOf('/') + 1)
    return last.includes('.') ? pathname : '/' + INDEX
}

/**
 * The platform's own UIs' hostnames, from the UI_HOSTS binding.
 *
 * @param {string} binding the comma-separated hostnames, possibly empty
 * @returns {Set<string>} the hostnames, lowercase
 */
export function platformUis(binding) {
    return new Set(binding.split(',').map(host => host.trim().toLowerCase()).filter(host => host.length > 0))
}

/**
 * How long the edge may keep a file: hashed assets forever, everything else briefly.
 *
 * @param {string} file the file's path within the site
 * @returns {number} seconds
 */
export function cacheTtl(file) {
    return file.startsWith(IMMUTABLE_PREFIX) ? IMMUTABLE_TTL_SECONDS : MUTABLE_TTL_SECONDS
}

/**
 * The cache-control header a browser receives with a file: hashed assets kept forever, everything else
 * revalidated on each use, so a new release's index is never served from a stale copy.
 *
 * @param {string} file the file's path within the site
 * @returns {string} the header's value
 */
export function cacheControl(file) {
    return cacheTtl(file) === IMMUTABLE_TTL_SECONDS
            ? 'public, max-age=' + IMMUTABLE_TTL_SECONDS + ', immutable'
            : 'no-cache'
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url)
        const host = url.hostname.toLowerCase()
        let site
        if (platformUis(env.UI_HOSTS).has(host)) {
            site = host
        } else {
            const resolved = resolveHost(host, env.SITES_DOMAIN.toLowerCase())
            if (resolved === null) {
                return new Response('Not found', { status: 404 })
            }
            if (resolved.site === null) {
                return Response.redirect(env.PORTAL_BASE_URL + '/login/' + encodeURIComponent(resolved.organizationId), 302)
            }
            site = resolved.site
        }
        if (request.method !== 'GET' && request.method !== 'HEAD') {
            return new Response('Method not allowed', { status: 405, headers: { Allow: 'GET, HEAD' } })
        }
        const file = resolveFile(url.pathname)
        const ttl = cacheTtl(file)
        // the container is private, so the read carries the SAS; the response alone reaches the browser
        const origin = env.SITES_STORAGE_URL + '/' + SITES_CONTAINER + '/' + site + file + '?' + env.SITES_STORAGE_SAS
        const answer = await fetch(origin, {
            method: request.method,
            cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': ttl, '404': 10, '500-599': 0 } },
        })
        // the storage account's own headers say nothing a browser needs; the file's type and length do
        const headers = new Headers()
        for (const name of ['content-type', 'content-length', 'etag', 'last-modified']) {
            const value = answer.headers.get(name)
            if (value !== null) {
                headers.set(name, value)
            }
        }
        headers.set('cache-control', cacheControl(file))
        return new Response(answer.body, { status: answer.status, headers })
    },
}
