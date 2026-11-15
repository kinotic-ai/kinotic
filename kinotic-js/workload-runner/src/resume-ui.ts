import { log, logError } from './log.ts'
import { copyFile, deleteBlob, INDEX_FILE, parseSiteUrl, readFile, SUSPENDED_INDEX_FILE, SUSPENDED_METADATA_HEADER } from './site-storage.ts'

/**
 * One-shot entrypoint of the site resumption workload: puts back the index a suspended site was published
 * with, through the index URL issued for that site, so the site is served again. A site that is not
 * suspended is left as it is. Fails for a suspended site whose kept index is gone, which a publish serves again.
 *
 * Environment:
 * - KINOTIC_UI_INDEX_URL  the site's directory in the sites account, with a SAS for that directory
 *                         as its query (required)
 * - KINOTIC_LOG_*         see log.ts
 */

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

async function main(): Promise<void> {
    const site = parseSiteUrl('KINOTIC_UI_INDEX_URL', require_('KINOTIC_UI_INDEX_URL'))
    const kept = await readFile(site, SUSPENDED_INDEX_FILE)
    if (kept !== null) {
        await copyFile(site, kept, INDEX_FILE)
        // deleted once the index is back, so a run that stops before it is done again from the kept index
        await deleteBlob(site, `${site.directory}/${SUSPENDED_INDEX_FILE}`)
        log(`[workload-runner] resumed site ${site.directory}`)
    } else if ((await readFile(site, INDEX_FILE))?.headers.get(SUSPENDED_METADATA_HEADER) === 'true') {
        throw new Error(`site ${site.directory} is suspended but its index is gone; publish the project again to serve it`)
    } else {
        log(`[workload-runner] site ${site.directory} is not suspended`)
    }
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] resumption failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
