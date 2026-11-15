import { log, logError } from './log.ts'
import {
    COMMIT_METADATA_HEADER,
    copyFile,
    INDEX_FILE,
    parseSiteUrl,
    readFile,
    SUSPENDED_INDEX_FILE,
    SUSPENDED_METADATA_HEADER,
    writeFile,
} from './site-storage.ts'

/**
 * One-shot entrypoint of the site suspension workload: serves one site a page that sends the browser to
 * where its organization's deactivation is explained, in place of its own index, through the index URL
 * issued for that site. The site's index is kept under its directory, for the resumption workload to put
 * back. A site already suspended, or never published, is left as it is.
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

function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/** The page a suspended site serves on every route: an immediate redirect, with a link for a browser that does not follow it. */
function suspendedPage(deactivatedUrl: string): string {
    const url = escapeHtml(deactivatedUrl)
    return '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
        + `<meta http-equiv="refresh" content="0; url=${url}">\n<title>Organization deactivated</title>\n</head>\n`
        + `<body>\n<p>This organization is deactivated. <a href="${url}">Find out more</a>.</p>\n</body>\n</html>\n`
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
        // the index is kept before it is replaced, so a run that stops between the two writes leaves the site served
        await copyFile(site, index.clone(), SUSPENDED_INDEX_FILE)
        const commit = index.headers.get(COMMIT_METADATA_HEADER)
        await writeFile(site, INDEX_FILE, new Blob([suspendedPage(deactivatedUrl)]), {
            'Content-Type': 'text/html; charset=utf-8',
            'x-ms-blob-cache-control': 'no-cache',
            [SUSPENDED_METADATA_HEADER]: 'true',
            // stamped with the published commit, so a publish's cleanup treats it as that commit's index
            ...(commit ? { [COMMIT_METADATA_HEADER]: commit } : {}),
        })
        log(`[workload-runner] suspended site ${site.directory}`)
    }
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] suspension failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
