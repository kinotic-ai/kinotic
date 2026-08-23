import { Kinotic } from '@kinotic-ai/core'
import { appZone, OsApiPlugin } from '@kinotic-ai/os-api'
import { synchronizeProject } from '@kinotic-ai/project-sync'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { writeSentinel } from './sentinel.ts'

/**
 * One-shot entrypoint of the sync workload: brings the shared checkout directory to the
 * requested commit, installs dependencies, synchronizes the project's entity definitions
 * with the server, and writes the reload sentinel the runtime workload's supervisor polls.
 *
 * The sentinel is written last, only after everything else succeeded — the supervisor
 * therefore never restarts the microservices into a half-updated tree.
 *
 * Environment:
 * - GIT_CLONE_URL            https URL of the repository to check out (required)
 * - GIT_REF                  commit sha or branch to deploy (required)
 * - GIT_TOKEN                token authorizing the fetch; omit for a public repository
 * - KINOTIC_WORKSPACE_DIR    the shared checkout directory (default /workspace)
 * - KINOTIC_ORGANIZATION_ID  the organization the project belongs to
 * - KINOTIC_APPLICATION_ID   the application the project belongs to
 * - KINOTIC_PROJECT_NAME     the project's name on the server
 * - KINOTIC_SERVER_* / KINOTIC_CLIENT_ID / KINOTIC_CLIENT_SECRET — standard Kinotic
 *   connection settings; entity sync is skipped when no credentials are present
 */

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

function run(command: string, args: string[], cwd: string, capture = false): string {
    const result = spawnSync(command, args, {
        cwd,
        stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit',
        encoding: 'utf-8',
    })
    if (result.error) {
        throw result.error
    }
    if (result.status !== 0) {
        throw new Error(`${command} ${args.join(' ')} exited with ${result.status}`)
    }
    return capture ? (result.stdout as string).trim() : ''
}

/**
 * Fetches the requested ref and checks it out detached, initializing the repository on the
 * first run. The same fetch-then-checkout flow serves first deployment and update alike,
 * and fetching the explicit ref keeps the transfer to one commit. Untracked files
 * (node_modules) survive between runs so installs stay incremental.
 */
function syncSource(workspaceDir: string, cloneUrl: string, ref: string, token: string | undefined): void {
    mkdirSync(workspaceDir, { recursive: true })
    if (!existsSync(join(workspaceDir, '.git'))) {
        run('git', ['init', '--initial-branch=main'], workspaceDir)
    }

    const remoteExists = spawnSync('git', ['remote', 'get-url', 'origin'], { cwd: workspaceDir }).status === 0
    run('git', ['remote', remoteExists ? 'set-url' : 'add', 'origin', cloneUrl], workspaceDir)

    // The token travels as a per-invocation config value, never `git config`-ed or embedded
    // in the remote URL: the checkout lives on a shared host directory the runtime workload
    // mounts, so nothing under .git may hold a credential
    const auth = token
        ? ['-c', `http.extraheader=AUTHORIZATION: basic ${Buffer.from(`x-access-token:${token}`).toString('base64')}`]
        : []
    run('git', [...auth, 'fetch', '--depth', '1', 'origin', ref], workspaceDir)
    run('git', ['checkout', '--force', '--detach', 'FETCH_HEAD'], workspaceDir)
}

/**
 * Synchronizes the project's committed entity definitions (.config/c3) and migrations
 * with the Kinotic server so they exist before the microservices serving them start.
 * Skipped when the environment carries no Kinotic credentials, so the checkout itself
 * still works against a server the workload cannot reach yet.
 */
async function syncEntities(workspaceDir: string): Promise<void> {
    if (!process.env.KINOTIC_CLIENT_ID && !process.env.KINOTIC_TOKEN) {
        console.log('[workload-runner] no Kinotic credentials in the environment; skipping entity sync')
        return
    }
    const organizationId = require_('KINOTIC_ORGANIZATION_ID')
    const applicationId = require_('KINOTIC_APPLICATION_ID')
    const projectName = require_('KINOTIC_PROJECT_NAME')

    // The workload authenticates as an APPLICATION-scoped machine, so it lives in the
    // application's zone the same way the project's microservices do
    Kinotic.use(OsApiPlugin)
    Kinotic.zonePrefix = appZone(organizationId, applicationId)
    await Kinotic.connect()
    try {
        await synchronizeProject({ organizationId, applicationId, projectName, projectDir: workspaceDir })
    } finally {
        await Kinotic.disconnect()
    }
}

async function main(): Promise<void> {
    const cloneUrl = require_('GIT_CLONE_URL')
    const ref = require_('GIT_REF')
    const token = process.env.GIT_TOKEN
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    console.log(`[workload-runner] syncing ${ref} into ${workspaceDir}`)
    syncSource(workspaceDir, cloneUrl, ref, token)
    run('bun', ['install'], workspaceDir)
    await syncEntities(workspaceDir)

    const commitSha = run('git', ['rev-parse', 'HEAD'], workspaceDir, true)
    writeSentinel(workspaceDir, commitSha)
    console.log(`[workload-runner] deployed ${commitSha}`)
}

try {
    await main()
} catch (error) {
    console.error('[workload-runner] sync failed:', error)
    process.exit(1)
}
