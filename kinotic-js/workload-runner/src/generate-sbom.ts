import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { blobUrl, deleteFilesOfOtherCommits, parseDirectoryUrl, uploadBlob } from './blob-directory.ts'
import { generateSbom } from './sbom.ts'
import { log, logError } from './log.ts'

/**
 * One-shot entrypoint of the SBOM workload: generates the project's SBOM from the checkout,
 * mounted read-only, uploads it into the project's SBOM directory of the organization storage
 * account through the URL issued for that directory, and then deletes the files other commits
 * left in the directory. The server records the SBOM once the workload has succeeded.
 *
 * Environment:
 * - KINOTIC_SBOM_UPLOAD_URL  the project's SBOM directory in the organization storage account,
 *                            with a SAS for that directory as its query (required)
 * - KINOTIC_SBOM_FILE        the name the document is uploaded under (required)
 * - KINOTIC_SBOM_COMMIT      the commit the checkout holds (required)
 * - KINOTIC_WORKSPACE_DIR    the checkout (default /workspace)
 * - KINOTIC_LOG_*            see log.ts
 */

/** The media type of a CycloneDX JSON document. */
const CYCLONEDX_JSON = 'application/vnd.cyclonedx+json'

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

async function main(): Promise<void> {
    const directory = parseDirectoryUrl('KINOTIC_SBOM_UPLOAD_URL', require_('KINOTIC_SBOM_UPLOAD_URL'))
    const file = require_('KINOTIC_SBOM_FILE')
    const commitSha = require_('KINOTIC_SBOM_COMMIT')
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    log(`[workload-runner] generating the SBOM of ${commitSha}`)
    const output = join(tmpdir(), file)
    const document = await generateSbom(workspaceDir, output)
    await uploadBlob(blobUrl(directory, file), Bun.file(output), 'no-cache', CYCLONEDX_JSON, commitSha)
    const stale = await deleteFilesOfOtherCommits(directory, commitSha)
    log(`[workload-runner] uploaded the SBOM of ${commitSha}: ${document.components?.length ?? 0} components; `
        + `deleted ${stale} file(s) of other commits`)
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] SBOM generation failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
