import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { type ChildProcess, spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
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

    afterEach(async () => {
        // SIGTERM lets the supervisor stop its microservice; a SIGKILL would orphan it
        if (supervisor) {
            const exited = new Promise<void>(resolve => supervisor!.once('exit', () => resolve()))
            supervisor.kill('SIGTERM')
            await Promise.race([exited, Bun.sleep(5_000).then(() => supervisor?.kill('SIGKILL'))])
        }
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

    describe('telemetry preload', () => {

        interface OtlpRequest {
            path: string
            authorization: string | null
            body: Buffer
        }

        let collector: ReturnType<typeof Bun.serve>
        let received: OtlpRequest[]

        beforeEach(() => {
            received = []
            collector = Bun.serve({
                hostname: '127.0.0.1',
                port: 0,
                async fetch(request) {
                    received.push({
                        path: new URL(request.url).pathname,
                        authorization: request.headers.get('authorization'),
                        body: Buffer.from(await request.arrayBuffer()),
                    })
                    return new Response(new Uint8Array(0))
                },
            })
            // The project resolves @opentelemetry/api from its own install, as a checkout does
            symlinkSync(join(import.meta.dir, '..', 'node_modules'), join(appDir, 'node_modules'))
            writeFileSync(join(appDir, 'service.ts'),
                          `import { appendFileSync } from 'node:fs'
                           import { trace } from '@opentelemetry/api'
                           trace.getTracer('service').startSpan('handle-request').end()
                           appendFileSync('starts.log', 'start\\n')
                           setInterval(() => {}, 1000)`)
        })

        afterEach(() => {
            collector.stop(true)
        })

        /** The environment the node lays into a guest holding an OTLP endpoint. */
        function otlpEnvironment(scheduleDelayMs: number): Record<string, string> {
            return {
                OTEL_EXPORTER_OTLP_ENDPOINT: `http://127.0.0.1:${collector.port}`,
                OTEL_EXPORTER_OTLP_PROTOCOL: 'http/protobuf',
                OTEL_EXPORTER_OTLP_HEADERS: 'authorization=Bearer%20secret-token',
                OTEL_TRACES_EXPORTER: 'otlp',
                OTEL_METRICS_EXPORTER: 'none',
                OTEL_LOGS_EXPORTER: 'none',
                OTEL_SERVICE_NAME: 'service-under-test',
                OTEL_BSP_SCHEDULE_DELAY: String(scheduleDelayMs),
            }
        }

        async function waitForTraces(timeoutMs: number): Promise<OtlpRequest> {
            const deadline = Date.now() + timeoutMs
            let ret = received.find(r => r.path === '/v1/traces')
            while (ret === undefined) {
                if (Date.now() > deadline) {
                    throw new Error(`no trace export arrived; requests: ${JSON.stringify(received.map(r => r.path))}`)
                }
                await Bun.sleep(50)
                ret = received.find(r => r.path === '/v1/traces')
            }
            return ret
        }

        it('exports the spans the microservice records through the OpenTelemetry API', async () => {
            startSupervisor(otlpEnvironment(100))
            await waitForStarts(1, 10_000)

            const export_ = await waitForTraces(10_000)
            expect(export_.authorization).toBe('Bearer secret-token')
            // Protobuf carries strings verbatim, so the span and service names are visible as bytes
            expect(export_.body.includes('handle-request')).toBe(true)
            expect(export_.body.includes('service-under-test')).toBe(true)
        }, 40_000)

        it('flushes pending spans when the microservice is stopped', async () => {
            // A schedule delay longer than the test means only the shutdown flush can export the span
            startSupervisor(otlpEnvironment(60_000))
            await waitForStarts(1, 10_000)

            const exited = new Promise<void>(resolve => supervisor!.once('exit', () => resolve()))
            supervisor!.kill('SIGTERM')
            await Promise.race([exited, Bun.sleep(10_000).then(() => { throw new Error('supervisor did not exit') })])
            supervisor = null

            expect(received.some(r => r.path === '/v1/traces' && r.body.includes('handle-request'))).toBe(true)
        }, 40_000)
    })
})
