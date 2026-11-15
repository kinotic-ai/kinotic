import { log, logError } from './log.ts'
import { copyFile, deleteBlob, INDEX_FILE, parseSiteUrl, readFile, SUSPENDED_INDEX_FILE, SUSPENDED_METADATA_HEADER } from './site-storage.ts'

/**
 * One-shot entrypoint of the site resumption workload: puts back the index a suspended site was published
 * with, through the index URL issued for that site, so the site is served again. A site that is not
 * suspended is left serving its index. Fails for a suspended site whose kept index is gone, which a publish
 * serves again, and when the index changes while it is being put back.
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
    const index = await readFile(site, INDEX_FILE)
    const kept = await readFile(site, SUSPENDED_INDEX_FILE)
    if (index?.headers.get(SUSPENDED_METADATA_HEADER) === 'true') {
        if (kept === null) {
            throw new Error(`site ${site.directory} is suspended but its index is gone; publish the project again to serve it`)
        }
        // the write requires the suspended index read, so an index a publish wrote meanwhile is never replaced
        await copyFile(site, kept, INDEX_FILE, { 'If-Match': index.headers.get('etag')! })
        log(`[workload-runner] resumed site ${site.directory}`)
    } else {
        log(`[workload-runner] site ${site.directory} is not suspended`)
    }
    // deleted once the index is back, so a run that stops before it is done again from the kept index
    if (kept !== null) {
        await deleteBlob(site, `${site.directory}/${SUSPENDED_INDEX_FILE}`)
    }
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] resumption failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
