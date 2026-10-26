import {spawn, spawnSync, type SpawnSyncReturns} from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import type {LocalStackVersions} from './LocalStackVersions'

/** The portal of the local stack, where organizations sign up and sign in. */
export const PORTAL_URL = 'http://localhost:9090'

/** The system console of the local stack, where system users sign in. */
export const CONSOLE_URL = 'http://localhost:9091'

const SERVER_HEALTH_URLS = ['http://localhost:58503/health', 'http://localhost:58504/health', 'http://localhost:58505/health']
const RECOMMENDED_MEMORY_BYTES = 12 * 1024 ** 3
const SERVER_START_TIMEOUT_MS = 15 * 60_000
const NODE_STOP_TIMEOUT_MS = 15_000

// The node connects to the system server as the SYSTEM-scope machine identity V2__system_identities seeds, and ships
// its workloads' logs, traces and metrics to the stack's Loki and OTLP collector
const NODE_ENVIRONMENT = {
    KINOTIC_NODE_ID: 'local-node',
    KINOTIC_SERVER_HOST: 'localhost',
    KINOTIC_SERVER_PORT: '58504',
    KINOTIC_CLIENT_ID: '00000000-0000-0000-0000-000000000011',
    KINOTIC_CLIENT_SECRET: 'kinotic',
    KINOTIC_LOKI_URL: 'http://localhost:3100',
    KINOTIC_TEMPO_URL: 'http://localhost:4318',
    KINOTIC_MIMIR_URL: 'http://localhost:4318',
}

/**
 * Kinotic on this machine: the docker-compose stack of its servers, portal, system console, Git server and
 * storage, and the vm-manager node that runs each deployment's workloads as micro VMs. Its files live under
 * {@link dir}: the compose files the stack runs from, the node's process id and its log.
 */
export class LocalStack {

    /** The directory holding the stack's compose files, the node's process id and the node's log. */
    readonly dir: string
    /** The node's log. */
    readonly nodeLog: string

    private readonly composeDir: string
    private readonly pidFile: string
    private readonly bundleDir: string
    private readonly versions: LocalStackVersions

    /**
     * @param dataDir the CLI's data directory, under which the stack keeps its files
     */
    constructor(dataDir: string) {
        this.dir = path.join(dataDir, 'local')
        this.composeDir = path.join(this.dir, 'compose')
        this.pidFile = path.join(this.dir, 'node.pid')
        this.nodeLog = path.join(this.dir, 'node.log')
        // dist/internal/local/LocalStack.js reads the dist/local the build bundles
        this.bundleDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'local')
        this.versions = JSON.parse(fs.readFileSync(path.join(this.bundleDir, 'stack.json'), 'utf8'))
    }

    /** The versions this CLI release runs the stack at. */
    get stackVersions(): LocalStackVersions {
        return this.versions
    }

    /** Whether this machine can run the node: macOS on Apple silicon, or Linux with KVM. */
    get canRunNode(): boolean {
        return (process.platform === 'darwin' && process.arch === 'arm64')
               || (process.platform === 'linux' && fs.existsSync('/dev/kvm'))
    }

    /**
     * Checks what the stack needs before it starts: Docker running with Compose v2, and Bun where the node runs.
     *
     * @returns warnings about what the stack can do without, such as less memory than it is sized for
     * @throws Error naming what is missing, when the stack cannot start
     */
    checkPrerequisites(): string[] {
        const warnings: string[] = []
        const info = this.run('docker', ['info', '--format', '{{.MemTotal}}'])
        if (info.error || info.status !== 0) {
            throw new Error('Docker is not running. Start Docker Desktop or the Docker daemon, then run this again.')
        }
        if (this.run('docker', ['compose', 'version']).status !== 0) {
            throw new Error('Docker Compose v2 is required (the `docker compose` command). Install or update Docker.')
        }
        if (Number(info.stdout.trim()) < RECOMMENDED_MEMORY_BYTES) {
            warnings.push('Docker has less than 12 GB of memory; Kinotic may run slowly or its servers may be stopped.'
                          + ' Raise Docker\'s memory limit if they are.')
        }
        if (this.canRunNode) {
            const bun = this.run('bun', ['--version'])
            if (bun.error || bun.status !== 0) {
                throw new Error('Bun is required to run the node your deployments run on. Install it from https://bun.sh.')
            }
        } else {
            warnings.push('This machine cannot run the node deployments run on (it needs macOS on Apple silicon, or Linux'
                          + ' with KVM), so Kinotic starts without one and projects do not deploy.')
        }
        return warnings
    }

    /**
     * Starts the stack's containers and waits for its servers to answer.
     *
     * @param pull whether to download the latest images first, rather than reuse those already on this machine
     * @param onWaiting called once the containers are started and the servers are being waited for
     */
    async start(pull: boolean, onWaiting: () => void): Promise<void> {
        // A fresh copy, so the containers' mounts never point into a CLI install that an upgrade replaces
        fs.rmSync(this.composeDir, {recursive: true, force: true})
        fs.cpSync(path.join(this.bundleDir, 'compose'), this.composeDir, {recursive: true})
        this.compose(['up', '-d', '--no-build', '--pull', pull ? 'always' : 'missing'])
        onWaiting()
        await this.waitForServers()
    }

    /**
     * Stops the stack's containers.
     *
     * @param deleteData whether to delete everything the stack stored, its organizations, projects and data
     */
    stop(deleteData: boolean): void {
        this.compose(deleteData ? ['down', '--volumes'] : ['down'])
    }

    /** Prints the state of each of the stack's containers. */
    printContainers(): void {
        this.compose(['ps', '--all'])
    }

    /**
     * Prints a container's logs, or every container's.
     *
     * @param service the compose service, or undefined for all of them
     * @param follow whether to keep printing new lines until interrupted
     */
    printContainerLogs(service: string | undefined, follow: boolean): void {
        const args = ['logs']
        if (follow) {
            args.push('--follow')
        }
        if (service) {
            args.push(service)
        }
        this.compose(args)
    }

    /** The node's process id while it runs, otherwise null. */
    nodePid(): number | null {
        let ret: number | null = null
        if (fs.existsSync(this.pidFile)) {
            const pid = Number(fs.readFileSync(this.pidFile, 'utf8').trim())
            try {
                process.kill(pid, 0)
                ret = pid
            } catch {
                // the process has exited; its id file is stale
            }
        }
        return ret
    }

    /**
     * Starts the node in the background, unless it runs already. It outlives this command, writing to
     * {@link nodeLog}, and the first start downloads its release.
     *
     * @returns the node's process id
     */
    startNode(): number {
        let ret = this.nodePid()
        if (ret === null) {
            fs.mkdirSync(this.dir, {recursive: true})
            const log = fs.openSync(this.nodeLog, 'a')
            const child = spawn('bunx', ['--package', `@kinotic-ai/vm-manager@${this.versions.vmManagerVersion}`,
                                         'kinotic-vm-manager'],
                                {detached: true, stdio: ['ignore', log, log], env: {...process.env, ...NODE_ENVIRONMENT}})
            child.unref()
            fs.closeSync(log)
            ret = child.pid!
            fs.writeFileSync(this.pidFile, String(ret))
        }
        return ret
    }

    /** Stops the node and waits for it to exit. */
    async stopNode(): Promise<void> {
        const pid = this.nodePid()
        if (pid !== null) {
            // detached made the node the leader of its own process group, which bunx's child process belongs to
            process.kill(-pid, 'SIGTERM')
            const deadline = Date.now() + NODE_STOP_TIMEOUT_MS
            while (this.nodePid() !== null && Date.now() < deadline) {
                await new Promise(resolve => setTimeout(resolve, 250))
            }
            if (this.nodePid() !== null) {
                process.kill(-pid, 'SIGKILL')
            }
        }
        fs.rmSync(this.pidFile, {force: true})
    }

    private async waitForServers(): Promise<void> {
        const deadline = Date.now() + SERVER_START_TIMEOUT_MS
        let pending = SERVER_HEALTH_URLS
        while (pending.length > 0) {
            if (Date.now() > deadline) {
                throw new Error(`Kinotic's servers did not start within ${SERVER_START_TIMEOUT_MS / 60_000} minutes.`
                                + ' See why with: kinotic local logs')
            }
            // a server that exits never answers, so its failure ends the wait rather than the deadline
            const exited = this.run('docker', ['ps', '--all', '--filter', 'name=^kinotic-server-', '--filter', 'status=exited',
                                               '--format', '{{.Names}}']).stdout.trim()
            if (exited) {
                const server = exited.split('\n')[0]
                throw new Error(`${server} stopped while starting. See why with: kinotic local logs ${server}`)
            }
            const answered = await Promise.all(pending.map(url => fetch(url).then(response => response.ok, () => false)))
            pending = pending.filter((url, index) => !answered[index])
            if (pending.length > 0) {
                await new Promise(resolve => setTimeout(resolve, 5000))
            }
        }
    }

    // Before the first start there is no copy of the compose files yet, and the bundled ones name the same project
    private compose(args: string[]): void {
        const dir = fs.existsSync(this.composeDir) ? this.composeDir : path.join(this.bundleDir, 'compose')
        const result = spawnSync('docker', ['compose', '--project-directory', dir, '-f', path.join(dir, 'compose.yml'), ...args],
                                 {stdio: 'inherit', env: {...process.env, kinoticVersion: this.versions.kinoticVersion}})
        if (result.error || result.status !== 0) {
            throw new Error(`docker compose ${args[0]} failed`)
        }
    }

    private run(command: string, args: string[]): SpawnSyncReturns<string> {
        return spawnSync(command, args, {encoding: 'utf8'})
    }
}
