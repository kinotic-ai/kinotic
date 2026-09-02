import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { writeSentinel } from './sentinel.ts'
import { forwardOutput, log, logError } from './log.ts'

/**
 * One-shot entrypoint of the sync workload: brings the shared checkout directory to the
 * requested commit, installs dependencies, synchronizes the project's entity definitions
 * with the server, and writes the reload sentinel the runtime workload's supervisor polls.
 *
 * The sentinel is written last, only after everything else succeeded — the supervisor
 * therefore never restarts the microservices into a half-updated tree.
 *
 * Environment:
 * - GIT_CLONE_URL          https URL of the repository to check out (required)
 * - GIT_REF                commit sha or branch to deploy (required)
 * - GIT_TOKEN              token authorizing the fetch; omit for a public repository
 * - KINOTIC_WORKSPACE_DIR  the shared checkout directory (default /workspace)
 * - KINOTIC_SERVER_* / KINOTIC_CLIENT_ID / KINOTIC_CLIENT_SECRET — standard Kinotic
 *   connection settings the CLI authenticates with; entity sync is skipped when no
 *   credentials are present
 * - KINOTIC_CLI_BIN        overrides the kinotic CLI entry script (development/tests)
 * - KINOTIC_LOG_*          see log.ts
 */

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

function run(command: string, args: string[], cwd: string): Promise<void> {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { cwd, stdio: ['ignore', 'pipe', 'pipe'] })
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
    if (!process.env.KINOTIC_CLIENT_ID && !process.env.KINOTIC_TOKEN) {
        log('[workload-runner] no Kinotic credentials in the environment; skipping entity sync')
        return
    }
    // A client credential only resolves when both halves are present, so an id whose secret
    // did not arrive would fall through to the CLI's stored-login path and fail with
    // "Not logged in" — which names neither the secret nor the deployment that dropped it
    if (process.env.KINOTIC_CLIENT_ID && !process.env.KINOTIC_CLIENT_SECRET) {
        throw new Error('KINOTIC_CLIENT_ID is set without KINOTIC_CLIENT_SECRET')
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
    await syncEntities(workspaceDir)

    const commitSha = headCommit(workspaceDir)
    writeSentinel(workspaceDir, commitSha)
    log(`[workload-runner] deployed ${commitSha}`)
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] sync failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
