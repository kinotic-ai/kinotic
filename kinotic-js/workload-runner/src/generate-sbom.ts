import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin } from '@kinotic-ai/management-api'
import { dependencyHashOf, readDependencies } from './sbom.ts'
import { log, logError } from './log.ts'

/**
 * One-shot entrypoint of the SBOM workload: reads the dependency tree of the checkout's bun.lock,
 * mounted read-only, and records it with the server as the project's SBOM against the dependency
 * hash of the checkout.
 *
 * Environment:
 * - KINOTIC_PROJECT_ID       the project the checkout belongs to (required)
 * - KINOTIC_WORKSPACE_DIR    the checkout (default /workspace)
 * - KINOTIC_SERVER_* / KINOTIC_CLIENT_ID / KINOTIC_CLIENT_SECRET — standard Kinotic connection
 *   settings, the project's sync machine identity the SBOM is recorded as
 * - KINOTIC_LOG_*            see log.ts
 */

Kinotic.use(ManagementApiPlugin)

/**
 * The largest tree one report carries: the gateway takes frames of up to 2 MiB, the default of
 * the server's maxEventPayloadSize, and the frame's headers and the call's other arguments need
 * room beside the tree.
 */
const MAX_TREE_BYTES = 2 * 1024 * 1024 - 64 * 1024

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

async function main(): Promise<void> {
    const projectId = require_('KINOTIC_PROJECT_ID')
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    const dependencyHash = dependencyHashOf(workspaceDir)
    if (dependencyHash === null) {
        throw new Error(`${workspaceDir} has no bun.lock to read the SBOM from`)
    }
    const dependencies = readDependencies(workspaceDir)
    const size = Buffer.byteLength(JSON.stringify(dependencies))
    if (size > MAX_TREE_BYTES) {
        throw new Error(`the SBOM of ${dependencies.packages.length} packages is ${size} bytes, `
                        + `more than the ${MAX_TREE_BYTES} one report to the server carries`)
    }
    log(`[workload-runner] read the SBOM: ${dependencies.packages.length} packages, `
        + `${dependencies.direct.length} declared by the project, ${dependencies.edges.length} dependencies between them`)

    // bounded so an unreachable server fails the run instead of retrying forever
    await Kinotic.connect({ maxConnectionAttempts: 3 })
    try {
        await Kinotic.projectArtifacts.recordSbom(projectId, dependencyHash, dependencies)
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
