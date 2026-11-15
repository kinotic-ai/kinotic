import { log, logError } from './log.ts'
import {
    COMMIT_METADATA_HEADER,
    copyFile,
    INDEX_FILE,
    parseSiteUrl,
    readFile,
    SUSPENDED_INDEX_FILE,
    SUSPENDED_METADATA_HEADER,
    unchangedSince,
    writeSuspendedIndex,
} from './site-storage.ts'

/**
 * One-shot entrypoint of the site suspension workload: serves one site a page that sends the browser to
 * where its organization's deactivation is explained, in place of its own index, through the index URL
 * issued for that site. The site's index is kept under its directory, for the resumption workload to put
 * back. A site already suspended, or never published, is left as it is. Fails when the index changes
 * while it is being replaced.
 *
 * Environment:
 * - KINOTIC_UI_INDEX_URL        the site's directory in the sites account, with a SAS for that directory
 *                               as its query (required)
 * - KINOTIC_UI_DEACTIVATED_URL  the page the site sends the browser to (required)
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
    const deactivatedUrl = require_('KINOTIC_UI_DEACTIVATED_URL')
    const index = await readFile(site, INDEX_FILE)
    if (index === null) {
        log(`[workload-runner] site ${site.directory} has no index, so there is nothing to suspend`)
    } else if (index.headers.get(SUSPENDED_METADATA_HEADER) === 'true') {
        log(`[workload-runner] site ${site.directory} is suspended already`)
    } else {
        // the index is kept before it is replaced, so a run that stops between the two writes leaves the site served;
        // both writes require the files as read, so a publish that changed either meanwhile fails the run, which is retried
        const kept = await readFile(site, SUSPENDED_INDEX_FILE)
        await copyFile(site, index.clone(), SUSPENDED_INDEX_FILE, unchangedSince(kept))
        await writeSuspendedIndex(site, deactivatedUrl, index.headers.get(COMMIT_METADATA_HEADER), unchangedSince(index))
        log(`[workload-runner] suspended site ${site.directory}`)
    }
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] suspension failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
