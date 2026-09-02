import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { type ChildProcess, spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const SUPERVISE = join(import.meta.dir, '..', 'src', 'supervise.ts')

describe('supervise entrypoint', () => {

    let appDir: string
    let supervisor: ChildProcess | null = null

    beforeEach(() => {
        appDir = join(tmpdir(), `workload-runner-supervise-${crypto.randomUUID()}`)
        mkdirSync(appDir, { recursive: true })
    })

    afterEach(() => {
        supervisor?.kill('SIGKILL')
        supervisor = null
        rmSync(appDir, { recursive: true, force: true })
    })

    /** Runs the supervisor the way its workload does: as a process over a checkout dir. */
    function startSupervisor(env: Record<string, string> = {}): void {
        supervisor = spawn('bun', [SUPERVISE], {
            env: {
                ...process.env,
                KINOTIC_APP_DIR: appDir,
                KINOTIC_APP_ENTRY: 'service.ts',
                KINOTIC_RELOAD_POLL_MS: '100',
                ...env,
            },
            stdio: 'ignore',
        })
    }

    function startCount(): number {
        try {
            return readFileSync(join(appDir, 'starts.log'), 'utf-8').split('\n').filter(Boolean).length
        } catch {
            return 0
        }
    }

    async function waitForStarts(count: number, timeoutMs: number): Promise<void> {
        const deadline = Date.now() + timeoutMs
        while (startCount() < count) {
            if (Date.now() > deadline) {
                throw new Error(`expected ${count} starts, saw ${startCount()}`)
            }
            await Bun.sleep(50)
        }
    }

    // Mirrors the sync entrypoint's atomic write + rename
    function writeSentinel(content: string): void {
        mkdirSync(join(appDir, '.kinotic'), { recursive: true })
        writeFileSync(join(appDir, '.kinotic', 'reload.tmp'), content)
        renameSync(join(appDir, '.kinotic', 'reload.tmp'), join(appDir, '.kinotic', 'reload'))
    }

    it('restarts the microservice when the sentinel changes', async () => {
        writeFileSync(join(appDir, 'service.ts'),
                      `import { appendFileSync } from 'node:fs'
                       appendFileSync('starts.log', 'start\\n')
                       setInterval(() => {}, 1000)`)
        startSupervisor()
        await waitForStarts(1, 10_000)

        writeSentinel('sha-1')
        await waitForStarts(2, 10_000)

        // A second write while running restarts again; an unchanged sentinel would not
        writeSentinel('sha-2')
        await waitForStarts(3, 10_000)
    }, 40_000)

    it('mirrors its own and the microservice output into the node log directory', async () => {
        const logDir = join(appDir, 'logs')
        mkdirSync(logDir)
        writeFileSync(join(appDir, 'service.ts'),
                      `import { appendFileSync } from 'node:fs'
                       appendFileSync('starts.log', 'start\\n')
                       console.log('service says hello')
                       console.error('service says oops')
                       setInterval(() => {}, 1000)`)
        startSupervisor({ KINOTIC_LOG_DIR: logDir, KINOTIC_LOG_MAX_SIZE_MB: '1', KINOTIC_LOG_MAX_FILES: '1' })
        await waitForStarts(1, 10_000)

        const deadline = Date.now() + 5_000
        let content = ''
        while (!content.includes('service says oops')) {
            if (Date.now() > deadline) {
                throw new Error(`log file never carried the service output: ${JSON.stringify(content)}`)
            }
            await Bun.sleep(50)
            content = existsSync(join(logDir, 'workload.log')) ? readFileSync(join(logDir, 'workload.log'), 'utf-8') : ''
        }
        expect(content).toContain('[workload-runner] starting service.ts')
        expect(content).toContain('service says hello')
    }, 40_000)

    it('respawns a crashed microservice', async () => {
        writeFileSync(join(appDir, 'service.ts'),
                      `import { appendFileSync } from 'node:fs'
                       appendFileSync('starts.log', 'start\\n')
                       process.exit(1)`)
        startSupervisor()

        // Two starts prove the crash respawn; the first backoff is one second
        await waitForStarts(2, 10_000)
    }, 40_000)
})
