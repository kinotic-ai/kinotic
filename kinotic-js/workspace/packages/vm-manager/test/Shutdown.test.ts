import { describe, expect, it } from 'bun:test'
import { spawn } from 'node:child_process'
import { join } from 'node:path'

// Ctrl+C on a running vm-manager, driven through the real entrypoint: `bun run dev` in its own
// process group, then SIGINT to that group — which is what the terminal does, and which lands
// on the node twice because `bun run` forwards the signal the group was already sent.
//
// The node points at a port nothing serves, so it is stuck in the connect Kinotic.connect()
// retries forever. That is the state a developer is most often in when they interrupt it, and
// the state whose shutdown used to hang: Kinotic.disconnect() serializes behind that connect.
// Signals and process groups are POSIX, so this runs anywhere the vm-manager does.
const canRun = process.platform !== 'win32'

const PACKAGE_DIR = join(import.meta.dir, '..')

/** Port the node is pointed at. Whatever answers there, it is not a Kinotic server. */
const DEAD_SERVER_PORT = '59999'

/** Ceiling on waiting for the node to exit, well past the entrypoint's own SHUTDOWN_TIMEOUT_MS. */
const EXIT_TIMEOUT_MS = 20_000

/** How long the node is given to reach the connect retry loop before it is interrupted. */
const STARTUP_MS = 4000

interface Termination {
    code: number | null
    output: string
    elapsedMs: number
}

/** Starts the node, interrupts it once startup has settled, and reports how it went down. */
async function interrupt(): Promise<Termination> {
    const child = spawn('bun', ['run', 'dev'], {
        cwd: PACKAGE_DIR,
        // Its own process group, so the SIGINT below reaches `bun run` and the node it spawned
        // exactly as a terminal's Ctrl+C would, instead of this test runner's group
        detached: true,
        stdio: ['ignore', 'pipe', 'pipe'],
        env: {
            ...process.env,
            KINOTIC_NODE_ID: 'shutdown-test-node',
            KINOTIC_SERVER_HOST: '127.0.0.1',
            KINOTIC_SERVER_PORT: DEAD_SERVER_PORT,
            KINOTIC_CLIENT_ID: 'shutdown-test',
            KINOTIC_CLIENT_SECRET: 'shutdown-test',
        },
    })

    let output = ''
    child.stdout.on('data', chunk => output += chunk)
    child.stderr.on('data', chunk => output += chunk)

    const exited = new Promise<number | null>(resolve => child.once('exit', code => resolve(code)))
    await Bun.sleep(STARTUP_MS)

    const startedAt = Date.now()
    process.kill(-child.pid!, 'SIGINT')
    const code = await Promise.race([exited, Bun.sleep(EXIT_TIMEOUT_MS).then(() => undefined)])
    const elapsedMs = Date.now() - startedAt

    if (code === undefined) {
        process.kill(-child.pid!, 'SIGKILL')
        await exited
    }
    return { code: code ?? null, output, elapsedMs }
}

describe.if(canRun)('vm-manager shutdown', () => {

    it('exits on a single Ctrl+C while the server is unreachable', async () => {
        const termination = await interrupt()

        expect(termination.output).toContain('Shutting down VM Manager...')
        expect(termination.code).toBe(0)
        // The graceful path ran to completion rather than the process being cut off by the
        // entrypoint's force-exit timer, which is what a reintroduced hang would look like
        expect(termination.output).not.toContain('exiting anyway')
        expect(termination.elapsedMs).toBeLessThan(5000)
    }, EXIT_TIMEOUT_MS + STARTUP_MS + 10_000)

})
