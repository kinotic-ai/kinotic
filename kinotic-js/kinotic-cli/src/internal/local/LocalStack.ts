import {spawn, spawnSync, type SpawnSyncReturns} from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import type {LocalStackVersions} from './LocalStackVersions'

/** The portal of the local stack, where organizations sign up and sign in. */
export const PORTAL_URL = 'http://localhost:9090'

/** The system console of the local stack, where system users sign in. */
export const CONSOLE_URL = 'http://localhost:9091'

// compose publishes the servers on 127.0.0.1 alone, which "localhost" may resolve past to ::1 first
const SERVER_HEALTH_URLS = ['http://127.0.0.1:58503/health', 'http://127.0.0.1:58504/health', 'http://127.0.0.1:58505/health']
// The project compose.yml names, which labels every container of the stack
const COMPOSE_PROJECT = 'kinotic'
const NODE_COMMAND = 'kinotic-vm-manager'
const RECOMMENDED_MEMORY_BYTES = 12 * 1024 ** 3
const SERVER_START_TIMEOUT_MS = 15 * 60_000
const NODE_STARTUP_CHECK_MS = 5000
const NODE_STOP_TIMEOUT_MS = 15_000

// The node connects to the system server as the SYSTEM-scope machine identity V2__system_identities seeds, and ships
// its workloads' logs, traces and metrics to the stack's Loki and OTLP collector
const NODE_ENVIRONMENT = {
    KINOTIC_NODE_ID: 'local-node',
    KINOTIC_SERVER_HOST: '127.0.0.1',
    KINOTIC_SERVER_PORT: '58504',
    KINOTIC_CLIENT_ID: '00000000-0000-0000-0000-000000000011',
    KINOTIC_CLIENT_SECRET: 'kinotic',
    KINOTIC_LOKI_URL: 'http://127.0.0.1:3100',
    KINOTIC_TEMPO_URL: 'http://127.0.0.1:4318',
    KINOTIC_MIMIR_URL: 'http://127.0.0.1:4318',
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
     * @throws Error when this CLI holds no bundled stack, as when it runs from its sources unbuilt
     */
    constructor(dataDir: string) {
        this.dir = path.join(dataDir, 'local')
        this.composeDir = path.join(this.dir, 'compose')
        this.pidFile = path.join(this.dir, 'node.pid')
        this.nodeLog = path.join(this.dir, 'node.log')
        // dist/internal/local/LocalStack.js reads the dist/local the build bundles
        this.bundleDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'local')
        const versionsFile = path.join(this.bundleDir, 'stack.json')
        if (!fs.existsSync(versionsFile)) {
            throw new Error(`This CLI has no bundled local stack at ${this.bundleDir}; build it with: pnpm build`)
        }
        this.versions = JSON.parse(fs.readFileSync(versionsFile, 'utf8'))
    }

    /** The versions this CLI release runs the stack at. */
    get stackVersions(): LocalStackVersions {
        return this.versions
    }

    /** Whether this machine can run the node: macOS on Apple silicon, or Linux with KVM this user may open. */
    get canRunNode(): boolean {
        let ret = process.platform === 'darwin' && process.arch === 'arm64'
        if (process.platform === 'linux') {
            try {
                fs.accessSync('/dev/kvm', fs.constants.R_OK | fs.constants.W_OK)
                ret = true
            } catch {
                // no KVM, or this user is not in the group that may open it
            }
        }
        return ret
    }

    /**
     * Checks what the stack needs before it starts: Docker running with Compose v2, and Bun where the node runs.
     *
     * @returns warnings about what the stack can do without, such as less memory than it is sized for
     * @throws Error naming what is missing, when the stack cannot start
     */
    checkPrerequisites(): string[] {
        const warnings: string[] = []
        const info = this.run('docker', ['info', '--format', '{{.MemTotal}} {{.DockerRootDir}}'])
        if (info.error) {
            throw new Error('Docker is required. Install Docker Desktop or Docker Engine (https://docs.docker.com/get-docker/).')
        }
        if (info.status !== 0) {
            throw new Error('Docker is not running, or this user may not use it: '
                            + `${info.stderr.trim().split('\n')[0]}. Start Docker, then run this again.`)
        }
        const [memTotal, rootDir] = info.stdout.trim().split(' ')
        if (rootDir.includes('/snap/')) {
            throw new Error('Docker installed as a snap cannot read the files Kinotic mounts from your home directory.'
                            + ' Install Docker Engine (https://docs.docker.com/engine/install/) instead.')
        }
        if (this.run('docker', ['compose', 'version']).status !== 0) {
            throw new Error('Docker Compose v2 is required (the `docker compose` command). Install or update Docker.')
        }
        if (Number(memTotal) < RECOMMENDED_MEMORY_BYTES) {
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
                          + ' with KVM and a user in its group), so Kinotic starts without one and projects do not deploy.')
        }
        return warnings
    }

    /**
     * Downloads the stack's latest images, keeping those already here when a download fails, then starts its
     * containers and waits for its servers to answer.
     *
     * @param onWaiting called once the containers are started and the servers are being waited for
     */
    async start(onWaiting: () => void): Promise<void> {
        // Copied over in place: a running container's bind mounts keep pointing at the same files and directories,
        // and none of them points into a CLI install that an upgrade replaces
        fs.cpSync(path.join(this.bundleDir, 'compose'), this.composeDir, {recursive: true, force: true})
        this.compose(['pull', '--ignore-pull-failures'])
        this.compose(['up', '-d', '--no-build', '--pull', 'missing'])
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
            // the id must still lead a process group running the node, not a process that reused the id
            if (Number.isInteger(pid) && pid > 0 && this.run('pgrep', ['-g', String(pid), '-f', NODE_COMMAND]).status === 0) {
                ret = pid
            }
        }
        return ret
    }

    /**
     * Starts the node in the background, unless it runs already, and checks that it keeps running. It outlives
     * this command, writing to {@link nodeLog}, and the first start downloads its release.
     *
     * @returns the node's process id
     * @throws Error when the node cannot start or exits right away, with the end of its log
     */
    async startNode(): Promise<number> {
        let ret = this.nodePid()
        if (ret === null) {
            fs.mkdirSync(this.dir, {recursive: true})
            const log = fs.openSync(this.nodeLog, 'a')
            // A shell's or a project's KINOTIC_* settings would point the node elsewhere, and Bun reads any .env in its
            // working directory, so the node runs from the stack's own directory with only the settings below
            const env = Object.fromEntries(Object.entries(process.env)
                                                 .filter(([name]) => !name.startsWith('KINOTIC_') && name !== 'BOXLITE_HOME'))
            const child = spawn('bun', ['x', '--package', `@kinotic-ai/vm-manager@${this.versions.vmManagerVersion}`, NODE_COMMAND],
                                {detached: true, cwd: this.dir, stdio: ['ignore', log, log], env: {...env, ...NODE_ENVIRONMENT}})
            fs.closeSync(log)
            if (child.pid === undefined) {
                throw new Error('The node could not start: Bun did not run. Check that `bun x` works.')
            }
            child.unref()
            ret = child.pid
            fs.writeFileSync(this.pidFile, String(ret))
            await new Promise(resolve => setTimeout(resolve, NODE_STARTUP_CHECK_MS))
            if (this.nodePid() === null) {
                fs.rmSync(this.pidFile, {force: true})
                const tail = fs.readFileSync(this.nodeLog, 'utf8').trim().split('\n').slice(-10).join('\n')
                throw new Error(`The node exited as it started. The end of ${this.nodeLog}:\n${tail}`)
            }
        }
        return ret
    }

    /** Stops the node and waits for every process of it to exit. */
    async stopNode(): Promise<void> {
        const pid = this.nodePid()
        if (pid !== null) {
            // detached made the node the leader of its own process group, which `bun x`'s child process belongs to, and
            // the group outlives its leader while that child shuts down
            this.signalGroup(pid, 'SIGTERM')
            const deadline = Date.now() + NODE_STOP_TIMEOUT_MS
            while (this.nodePid() !== null && Date.now() < deadline) {
                await new Promise(resolve => setTimeout(resolve, 250))
            }
            if (this.nodePid() !== null) {
                this.signalGroup(pid, 'SIGKILL')
            }
        }
        fs.rmSync(this.pidFile, {force: true})
    }

    private signalGroup(pid: number, signal: NodeJS.Signals): void {
        try {
            process.kill(-pid, signal)
        } catch {
            // the group exited between the check and the signal
        }
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
            const exited = this.run('docker', ['ps', '--all', '--filter', `label=com.docker.compose.project=${COMPOSE_PROJECT}`,
                                               '--filter', 'name=^kinotic-server-', '--filter', 'status=exited',
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
