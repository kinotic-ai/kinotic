import { describe, expect, it } from 'bun:test'
import { spawn, spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { AlloyManager } from '@/internal/api/logging/AlloyManager'
import type { LogTarget } from '@/internal/api/model/LogTarget'
import { LogFormat } from '@/internal/api/model/LogFormat'

// AlloyManager resolves `alloy` from the PATH, so a harmless stand-in on the PATH lets
// the full start path (binary resolution, orphan takeover, spawn, pid file) run for real.
// It ignores SIGHUP as Alloy does on a config reload, and never execs, so the config path
// the manager passed stays in its argv where liveAlloys can find it. The run is bounded so
// a leaked stand-in cannot hold the test runner's inherited stdio open indefinitely.
const fakeBinDir = mkdtempSync(join(tmpdir(), 'alloy-bin-'))
writeFileSync(join(fakeBinDir, 'alloy'),
              "#!/bin/sh\ntrap '' HUP\ni=0\nwhile [ $i -lt 150 ]; do sleep 0.2; i=$((i+1)); done\n")
chmodSync(join(fakeBinDir, 'alloy'), 0o755)
process.env.PATH = `${fakeBinDir}:${process.env.PATH}`

function manager(dataDir: string): AlloyManager {
    return new AlloyManager({ lokiUrl: 'http://loki:3100', nodeId: 'node-1', dataDir })
}

function target(overrides: Partial<LogTarget> = {}): LogTarget {
    return {
        workloadId: '9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c',
        vmId: 'KeUwLBZv2RFz',
        logPath: '/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c/*.log',
        format: LogFormat.PLAIN,
        organizationId: 'acme',
        applicationId: null,
        ...overrides,
    }
}

/** Applies the targets and returns the pipeline config the manager wrote. */
async function configFor(targets: LogTarget[]): Promise<string> {
    const dataDir = mkdtempSync(join(tmpdir(), 'alloy-config-'))
    const alloyManager = manager(dataDir)
    try {
        await alloyManager.applyTargets(targets)
        return readFileSync(join(dataDir, 'config.alloy'), 'utf-8')
    } finally {
        await alloyManager.stop()
    }
}

describe('applyTargets pipeline generation', () => {

    it('routes a workload with an organization to that org tenant', async () => {
        const config = await configFor([target()])

        expect(config).toContain('tenant         = "acme"')
        expect(config).toContain('workload_id    = "9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c"')
        expect(config).toContain('vm_id          = "KeUwLBZv2RFz"')
        expect(config).toContain('node_id        = "node-1"')
        expect(config).toContain('__path__       = "/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c/*.log"')
    })

    it('unwraps the json-file envelope for a Docker target before tenant routing', async () => {
        const config = await configFor([target({
            logPath: '/var/lib/docker/containers/abc123/abc123-json.log',
            format: LogFormat.DOCKER_JSON,
        })])

        expect(config).toContain('stage.docker {}')
        expect(config).toContain('forward_to = [loki.process.docker.receiver]')
        expect(config).toContain('__path__       = "/var/lib/docker/containers/abc123/abc123-json.log"')
        // The docker stage recovers the message and hands off to the one tenant-routing stage
        expect(config).toContain('tenant         = "acme"')
    })

    it('omits the docker stage when no target needs it', async () => {
        const config = await configFor([target()])

        expect(config).not.toContain('stage.docker')
        expect(config).toContain('forward_to = [loki.process.workloads.receiver]')
    })

    it('routes a platform workload (no organization) to the system tenant', async () => {
        const config = await configFor([target({ organizationId: null })])

        expect(config).toContain('tenant         = "kinotic-system"')
    })

    it('labels application_id only when the workload has one', async () => {
        const withApp = await configFor([target({ applicationId: 'app-7' })])
        const withoutApp = await configFor([target()])

        expect(withApp).toContain('application_id = "app-7"')
        expect(withoutApp).not.toContain('application_id')
    })

    it('rescans a target path every second, so a short-lived run is discovered while it writes', async () => {
        const config = await configFor([target()])

        expect(config).toContain('sync_period  = "1s"')
    })

    it('derives valid component names from UUID workload ids', async () => {
        const config = await configFor([target()])

        expect(config).toContain('local.file_match "wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c"')
        expect(config).toContain('targets    = local.file_match.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.targets')
    })

    it('routes the tenant via a transient label that is dropped before push', async () => {
        const config = await configFor([target()])

        expect(config).toContain('stage.tenant')
        expect(config).toContain('source = "tenant"')
        expect(config).toContain('values = ["tenant"]')
    })

    it('renders write and process stages even with no targets', async () => {
        const config = await configFor([])

        expect(config).toContain('loki.write "default"')
        expect(config).toContain('url = "http://loki:3100/loki/api/v1/push"')
        expect(config).not.toContain('loki.source.file')
    })
})

function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0)
        return true
    } catch {
        return false
    }
}

describe('orphaned Alloy takeover on start', () => {

    it('terminates the process recorded in the pid file before starting', async () => {
        const orphan = spawn('sleep', ['60'])
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-takeover-'))
        writeFileSync(join(dataDir, 'alloy.pid'), String(orphan.pid))
        const alloyManager = manager(dataDir)

        await alloyManager.applyTargets([])

        expect(isAlive(orphan.pid!)).toBeFalse()
        await alloyManager.stop()
    })

    it('tolerates a recorded process that already exited', async () => {
        const exited = spawn('true')
        await new Promise(resolve => exited.once('exit', resolve))
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-takeover-'))
        writeFileSync(join(dataDir, 'alloy.pid'), String(exited.pid))
        const alloyManager = manager(dataDir)

        await alloyManager.applyTargets([])

        await alloyManager.stop()
    })
})

/**
 * Alloy processes still running for the given data dir, found by the config path the
 * manager passed on the command line. Only a process the manager lost track of can
 * outlive its stop, so a non-empty result is a leaked Alloy.
 */
async function liveAlloys(dataDir: string): Promise<string[]> {
    // A just-spawned process needs a moment to appear in the process table
    await Bun.sleep(250)
    const ps = spawnSync('ps', ['-eo', 'pid=,args='], { encoding: 'utf-8' })
    return ps.stdout.split('\n').filter(line => line.includes(dataDir))
}

describe('concurrent applyTargets', () => {

    it('spawns one Alloy and applies the last targets when calls overlap a start', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-concurrent-'))
        const alloyManager = manager(dataDir)

        // Every vm-manager workload operation ends in applyTargets, so overlapping
        // startWorkload calls land here while the first start is still spawning
        await Promise.all([
            alloyManager.applyTargets([]),
            alloyManager.applyTargets([target()]),
            alloyManager.applyTargets([target({ workloadId: 'ff000000-0000-4000-8000-000000000001' })]),
        ])
        await alloyManager.stop()

        expect(await liveAlloys(dataDir)).toBeEmpty()
        expect(readFileSync(join(dataDir, 'config.alloy'), 'utf-8'))
            .toContain('workload_id    = "ff000000-0000-4000-8000-000000000001"')
    })

    it('ignores targets applied after stop so shutdown leaves no Alloy behind', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-post-stop-'))
        const alloyManager = manager(dataDir)

        await alloyManager.stop()
        await alloyManager.applyTargets([target()])

        expect(await liveAlloys(dataDir)).toBeEmpty()
    })
})
