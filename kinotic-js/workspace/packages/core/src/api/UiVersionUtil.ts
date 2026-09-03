import type { UiVersionCheck } from './UiVersionCheck'

/**
 * Tells an open tab of a published UI whether the site has moved on to another commit. A
 * publish switches a site to a new commit atomically while keeping the previous commit's assets,
 * so a tab built from the previous commit keeps working; this is how it learns to offer a
 * reload. The site publishes the commit it serves as `version.json` next to its `index.html`,
 * never cached, and the build embeds the commit the tab was built from as `KINOTIC_UI_COMMIT`.
 * @param builtCommit the commit the running tab was built from
 * @param versionUrl where the site publishes the commit it serves; the root `version.json` unless
 *                   the UI is served under a path
 * @returns whether the tab is stale and which commit the site serves, never rejecting: a site
 *          whose version cannot be read leaves the tab not stale
 */
export async function checkUiVersion(builtCommit: string, versionUrl: string = '/version.json'): Promise<UiVersionCheck> {
    if (!builtCommit) {
        throw new Error('builtCommit is required')
    }
    let servedCommit: string | null = null
    try {
        const response = await fetch(versionUrl, { cache: 'no-store' })
        if (response.ok) {
            const body = await response.json() as { commitSha?: unknown }
            servedCommit = typeof body.commitSha === 'string' && body.commitSha.length > 0 ? body.commitSha : null
        }
    } catch {
        // unreachable or malformed: a tab is not told to reload on a guess
    }
    return { stale: servedCommit !== null && servedCommit !== builtCommit, servedCommit }
}
