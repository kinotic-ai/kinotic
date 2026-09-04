import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin, type ProjectArtifacts, type UiArtifact } from '@kinotic-ai/management-api'
import { findArtifacts } from './artifacts.ts'
import { writeSentinel } from './sentinel.ts'
import { forwardOutput, log, logError } from './log.ts'

/**
 * One-shot entrypoint of the sync workload: brings the shared checkout directory to the
 * requested commit, installs dependencies, finds the artifacts the commit contains,
 * synchronizes the project's entity definitions with the server, builds the UIs, reports
 * the artifacts to the server, and writes the reload sentinel the runtime workload's
 * supervisor polls.
 *
 * The sentinel is written last, only after everything else succeeded — the supervisor
 * therefore never restarts the microservices into a half-updated tree.
 *
 * Environment:
 * - GIT_CLONE_URL          https URL of the repository to check out (required)
 * - GIT_REF                commit sha or branch to deploy (required)
 * - GIT_TOKEN              token authorizing the fetch; omit for a public repository
 * - KINOTIC_WORKSPACE_DIR  the shared checkout directory (default /workspace)
 * - KINOTIC_PROJECT_ID     the project the checkout belongs to; required to report artifacts
 * - KINOTIC_UI_SERVER_URL  the address a browser reaches the platform on, handed to every UI
 *                          build together with KINOTIC_UI_COMMIT
 * - KINOTIC_SERVER_* / KINOTIC_CLIENT_ID / KINOTIC_CLIENT_SECRET — standard Kinotic
 *   connection settings the CLI and the artifact report authenticate with; both are
 *   skipped when no credentials are present
 * - KINOTIC_CLI_BIN        overrides the kinotic CLI entry script (development/tests)
 * - KINOTIC_LOG_*          see log.ts
 */

Kinotic.use(ManagementApiPlugin)

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

function run(command: string, args: string[], cwd: string, env: Record<string, string> = {}): Promise<void> {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] })
        forwardOutput(child)
        child.on('error', reject)
        child.on('exit', (code, signal) => {
            if (code === 0) {
                resolve()
            } else {
                reject(new Error(`${command} ${args.join(' ')} exited with ${code ?? signal}`))
            }
        })
    })
}

function headCommit(workspaceDir: string): string {
    const result = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: workspaceDir, encoding: 'utf-8' })
    if (result.status !== 0) {
        throw new Error(`git rev-parse HEAD exited with ${result.status}: ${result.stderr}`)
    }
    return result.stdout.trim()
}

/**
 * Fetches the requested ref and checks it out detached, initializing the repository on the
 * first run. The same fetch-then-checkout flow serves first deployment and update alike,
 * and fetching the explicit ref keeps the transfer to one commit. Untracked files
 * (node_modules) survive between runs so installs stay incremental.
 */
async function syncSource(workspaceDir: string, cloneUrl: string, ref: string, token: string | undefined): Promise<void> {
    mkdirSync(workspaceDir, { recursive: true })
    if (!existsSync(join(workspaceDir, '.git'))) {
        await run('git', ['init', '--initial-branch=main'], workspaceDir)
    }

    const remoteExists = spawnSync('git', ['remote', 'get-url', 'origin'], { cwd: workspaceDir }).status === 0
    await run('git', ['remote', remoteExists ? 'set-url' : 'add', 'origin', cloneUrl], workspaceDir)

    // The token travels as a per-invocation config value, never `git config`-ed or embedded
    // in the remote URL: the checkout lives on a shared host directory the runtime workload
    // mounts, so nothing under .git may hold a credential
    const auth = token
        ? ['-c', `http.extraheader=AUTHORIZATION: basic ${Buffer.from(`x-access-token:${token}`).toString('base64')}`]
        : []
    await run('git', [...auth, 'fetch', '--depth', '1', 'origin', ref], workspaceDir)
    await run('git', ['checkout', '--force', '--detach', 'FETCH_HEAD'], workspaceDir)
}

/**
 * Runs `kinotic sync --publish` over the checkout: generation compiles the entity sources
 * and refreshes the definitions — the projects have no CI of their own, so this deploy run
 * is where a project that does not build gets stopped — then the definitions and migrations
 * are pushed and every entity is published, leaving it usable for data operations. The CLI
 * authenticates with the machine identity in the environment; the sync is skipped entirely
 * when no credentials are present, so the checkout itself still works against a server the
 * workload cannot reach yet.
 */
async function syncEntities(workspaceDir: string): Promise<void> {
    if (!hasCredentials()) {
        log('[workload-runner] no Kinotic credentials in the environment; skipping entity sync')
        return
    }
    // bun runs the CLI's entry script directly, so its node shebang never matters
    const cliEntry = process.env.KINOTIC_CLI_BIN
        ?? Bun.resolveSync('@kinotic-ai/kinotic-cli/bin/run.js', import.meta.dir)
    // --publish creates the backing index for an entity the deploy just introduced; without
    // it the definition lands unpublished and every repository call against it fails. The
    // flag only acts on definitions that are not published yet, so redeploys are a no-op for
    // entities already serving data.
    await run('bun', [cliEntry, 'sync', '--publish', '--server', serverUrlFromEnv()], workspaceDir)
}

/**
 * Whether the environment carries a machine identity to connect with. The steps that talk to
 * the server are skipped without one, so the checkout itself still works against a server
 * the workload cannot reach yet.
 */
function hasCredentials(): boolean {
    if (!process.env.KINOTIC_CLIENT_ID && !process.env.KINOTIC_TOKEN) {
        return false
    }
    // A client credential only resolves when both halves are present, so an id whose secret
    // did not arrive would fall through to the CLI's stored-login path and fail with
    // "Not logged in" — which names neither the secret nor the deployment that dropped it
    if (process.env.KINOTIC_CLIENT_ID && !process.env.KINOTIC_CLIENT_SECRET) {
        throw new Error('KINOTIC_CLIENT_ID is set without KINOTIC_CLIENT_SECRET')
    }
    return true
}

/**
 * Reports the artifacts found in the checkout to the server, which records them on the
 * project's deployment for the deployment run to bind into itself once this workload has
 * exited. The connection resolves its server and credentials from the same KINOTIC_*
 * variables the CLI reads.
 */
async function reportArtifacts(commitSha: string, artifacts: ProjectArtifacts): Promise<void> {
    if (!hasCredentials()) {
        log('[workload-runner] no Kinotic credentials in the environment; skipping the artifact report')
        return
    }
    const projectId = require_('KINOTIC_PROJECT_ID')
    // The entity sync just reached the server, so a failure here is not worth a long retry:
    // bounded so an unreachable server fails the run instead of retrying forever
    await Kinotic.connect({ maxConnectionAttempts: 3 })
    try {
        await Kinotic.projectArtifacts.recordArtifacts(projectId, commitSha, artifacts)
    } finally {
        await Kinotic.disconnect()
    }
}

/**
 * Builds every UI of the commit in place, each with the three variables its build honors:
 * the base path its assets are served under, which is the commit so they can be cached
 * forever, the commit itself for the stale-tab check, and the server address. A build that
 * leaves no {@code dist/index.html} fails the run before the sentinel is written.
 */
async function buildUis(workspaceDir: string, uis: UiArtifact[], commitSha: string): Promise<void> {
    const env: Record<string, string> = { KINOTIC_UI_COMMIT: commitSha }
    if (process.env.KINOTIC_UI_SERVER_URL) {
        env.KINOTIC_UI_SERVER_URL = process.env.KINOTIC_UI_SERVER_URL
    }
    for (const ui of uis) {
        log(`[workload-runner] building UI ${ui.name}`)
        await run('bun', ['run', 'build'], join(workspaceDir, ui.dir), env)
        if (!existsSync(join(workspaceDir, ui.dir, 'dist', 'index.html'))) {
            throw new Error(`UI ${ui.name} (${ui.dir}) built without writing dist/index.html`)
        }
    }
}

function serverUrlFromEnv(): string {
    const host = require_('KINOTIC_SERVER_HOST')
    const port = process.env.KINOTIC_SERVER_PORT
    const useSsl = process.env.KINOTIC_SERVER_USE_SSL === 'true'
    return `${useSsl ? 'https' : 'http'}://${host}${port ? `:${port}` : ''}`
}

async function main(): Promise<void> {
    const cloneUrl = require_('GIT_CLONE_URL')
    const ref = require_('GIT_REF')
    const token = process.env.GIT_TOKEN
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    log(`[workload-runner] syncing ${ref} into ${workspaceDir}`)
    await syncSource(workspaceDir, cloneUrl, ref, token)
    await run('bun', ['install'], workspaceDir)
    // found before the entity sync so a commit with a bad package name fails fast
    const artifacts = findArtifacts(workspaceDir)
    log(`[workload-runner] found ${artifacts.microservices.length} microservice(s) and ${artifacts.uis.length} UI(s)`)
    await syncEntities(workspaceDir)

    const commitSha = headCommit(workspaceDir)
    await buildUis(workspaceDir, artifacts.uis, commitSha)
    await reportArtifacts(commitSha, artifacts)
    writeSentinel(workspaceDir, commitSha)
    log(`[workload-runner] deployed ${commitSha}`)
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] sync failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
