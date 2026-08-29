import { type ChildProcess, spawn } from 'node:child_process'
import { watchFile } from 'node:fs'
import { sentinelPath } from './sentinel.ts'

/**
 * Long-lived entrypoint of the runtime workload: runs the project's microservice process
 * and restarts it whenever the reload sentinel changes or the process dies.
 *
 * The sentinel is polled with fs.watchFile rather than watched: the sync workload writes
 * it from another VM sharing the checkout mount, and inotify events do not cross the VM
 * boundary — only polling observes the change. A full process restart gives the
 * microservices a fresh module graph, which is what a code update requires.
 *
 * Environment:
 * - KINOTIC_APP_DIR         the read-only checkout mount (default /app)
 * - KINOTIC_APP_ENTRY       entry file relative to the checkout
 *                           (default packages/microservices/main/src/main.ts)
 * - KINOTIC_RELOAD_POLL_MS  sentinel poll interval (default 1000)
 */

const appDir = process.env.KINOTIC_APP_DIR ?? '/app'
const entry = process.env.KINOTIC_APP_ENTRY ?? 'packages/microservices/main/src/main.ts'
const pollMs = Number(process.env.KINOTIC_RELOAD_POLL_MS ?? '1000')

// Crash respawns back off doubling from 1s to 30s; a run that survives 30s resets it
const INITIAL_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 30_000
const STABLE_RUN_MS = 30_000

let child: ChildProcess | null = null
let startedAt = 0
let backoffMs = INITIAL_BACKOFF_MS
let pendingRespawn: Timer | null = null
let reloading = false
let shuttingDown = false

function startChild(): void {
    pendingRespawn = null
    reloading = false
    startedAt = Date.now()
    console.log(`[workload-runner] starting ${entry}`)
    child = spawn('bun', [entry], { cwd: appDir, stdio: 'inherit' })
    child.on('exit', (code, signal) => {
        child = null
        if (shuttingDown) {
            process.exit(0)
        } else if (reloading) {
            startChild()
        } else {
            if (Date.now() - startedAt >= STABLE_RUN_MS) {
                backoffMs = INITIAL_BACKOFF_MS
            }
            console.error(`[workload-runner] microservice exited (code ${code}, signal ${signal}); `
                          + `restarting in ${backoffMs / 1000}s`)
            pendingRespawn = setTimeout(startChild, backoffMs)
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS)
        }
    })
}

function reload(): void {
    backoffMs = INITIAL_BACKOFF_MS
    if (child !== null) {
        reloading = true
        child.kill('SIGTERM')
    } else if (pendingRespawn !== null) {
        // Crashed and waiting out a backoff — the new code may be the fix, start it now
        clearTimeout(pendingRespawn)
        startChild()
    }
}

function shutdown(): void {
    shuttingDown = true
    if (pendingRespawn !== null) {
        clearTimeout(pendingRespawn)
    }
    if (child !== null) {
        child.kill('SIGTERM')
    } else {
        process.exit(0)
    }
}

process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)

watchFile(sentinelPath(appDir), { interval: pollMs }, (curr, prev) => {
    // Creation and every rewrite change the mtime; deletion (curr gone) is not a reload
    if (curr.mtimeMs !== prev.mtimeMs && curr.mtimeMs !== 0) {
        console.log('[workload-runner] reload sentinel changed; restarting microservice')
        reload()
    }
})

startChild()
