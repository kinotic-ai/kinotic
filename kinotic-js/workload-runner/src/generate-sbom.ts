import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin } from '@kinotic-ai/management-api'
import { uploadBlob } from './blob-directory.ts'
import { dependencyHashOf, generateSbom } from './sbom.ts'
import { log, logError } from './log.ts'

/**
 * One-shot entrypoint of the SBOM workload: generates the project's SBOM from the checkout,
 * mounted read-only, uploads it over the project's SBOM file in the organization storage account
 * through the URL issued for that file, and records it with the server against the dependency
 * hash of the checkout.
 *
 * Environment:
 * - KINOTIC_SBOM_UPLOAD_URL  the project's SBOM file in the organization storage account, with a
 *                            SAS for that file as its query (required)
 * - KINOTIC_PROJECT_ID       the project the checkout belongs to (required)
 * - KINOTIC_WORKSPACE_DIR    the checkout (default /workspace)
 * - KINOTIC_SERVER_* / KINOTIC_CLIENT_ID / KINOTIC_CLIENT_SECRET — standard Kinotic connection
 *   settings, the project's sync machine identity the SBOM is recorded as
 * - KINOTIC_LOG_*            see log.ts
 */

Kinotic.use(ManagementApiPlugin)

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
    const uploadUrl = require_('KINOTIC_SBOM_UPLOAD_URL')
    const projectId = require_('KINOTIC_PROJECT_ID')
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    const dependencyHash = dependencyHashOf(workspaceDir)
    if (dependencyHash === null) {
        throw new Error(`${workspaceDir} has no bun.lock to generate the SBOM from`)
    }
    log('[workload-runner] generating the SBOM')
    const output = join(tmpdir(), 'sbom.cdx.json')
    const document = await generateSbom(workspaceDir, output)
    const componentCount = document.components?.length ?? 0
    await uploadBlob(uploadUrl, Bun.file(output), 'no-cache', CYCLONEDX_JSON)
    log(`[workload-runner] uploaded the SBOM: ${componentCount} components`)

    // bounded so an unreachable server fails the run instead of retrying forever
    await Kinotic.connect({ maxConnectionAttempts: 3 })
    try {
        await Kinotic.projectArtifacts.recordSbom(projectId, dependencyHash)
    } finally {
        await Kinotic.disconnect()
    }
    log('[workload-runner] recorded the SBOM')
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] SBOM generation failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
