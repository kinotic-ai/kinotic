import { log, logError } from './log.ts'
import { deleteDirectory, parseSiteUrl } from './site-storage.ts'

/**
 * One-shot entrypoint of the site removal workload: deletes one site's directory in the
 * platform's sites storage account, through the removal URL issued for that site.
 *
 * Environment:
 * - KINOTIC_UI_REMOVAL_URL  the site's directory in the sites account, with a SAS for that
 *                           directory as its query (required)
 * - KINOTIC_LOG_*           see log.ts
 */

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

async function main(): Promise<void> {
    const site = parseSiteUrl('KINOTIC_UI_REMOVAL_URL', require_('KINOTIC_UI_REMOVAL_URL'))
    log(`[workload-runner] removing site ${site.directory}`)
    await deleteDirectory(site)
    log(`[workload-runner] removed site ${site.directory}`)
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] removal failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
