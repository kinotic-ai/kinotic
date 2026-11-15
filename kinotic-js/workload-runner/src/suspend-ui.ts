import { log, logError } from './log.ts'
import {
    COMMIT_METADATA_HEADER,
    copyFile,
    INDEX_FILE,
    parseSiteUrl,
    readFile,
    SUSPENDED_INDEX_FILE,
    SUSPENDED_METADATA_HEADER,
    withIndexLease,
    writeSuspendedIndex,
} from './site-storage.ts'

/**
 * One-shot entrypoint of the site suspension workload: serves one site a page saying its organization is
 * deactivated, in place of its own index, through the index URL
 * issued for that site. The site's index is kept under its directory, for the resumption workload to put
 * back. A site already suspended, or never published, is left as it is. Waits for a resumption or
 * publish acting on the site's index to finish first.
 *
 * Environment:
 * - KINOTIC_UI_INDEX_URL        the site's directory in the sites account, with a SAS for that directory
 *                               as its query (required)
 * - KINOTIC_LOG_*               see log.ts
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
    await withIndexLease(site, async (indexHeaders, keptHeaders) => {
        const index = await readFile(site, INDEX_FILE)
        if (index === null) {
            log(`[workload-runner] site ${site.directory} has no index, so there is nothing to suspend`)
        } else if (index.headers.get(SUSPENDED_METADATA_HEADER) === 'true') {
            log(`[workload-runner] site ${site.directory} is suspended already`)
        } else {
            // the index is kept before it is replaced, so a run that stops between the two writes leaves the site served
            await copyFile(site, index.clone(), SUSPENDED_INDEX_FILE, keptHeaders)
            await writeSuspendedIndex(site, index.headers.get(COMMIT_METADATA_HEADER), indexHeaders)
            log(`[workload-runner] suspended site ${site.directory}`)
        }
    })
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] suspension failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
