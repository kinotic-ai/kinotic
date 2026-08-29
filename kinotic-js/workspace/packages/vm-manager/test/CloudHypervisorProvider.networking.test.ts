import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { mkdirSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { spawnSync } from 'node:child_process'
import Docker from 'dockerode'
import { Workload, WorkloadStatus } from '@kinotic-ai/system-api'
import { CloudHypervisorProvider, KATA_CLH_RUNTIME } from '@/internal/api/providers/CloudHypervisorProvider'
import { NetnsAnchorManager } from '@/internal/api/network/NetnsAnchorManager'

// Whether a workload has a NIC is only answerable on a node that boots real micro VMs: the
// anchor exists because Kata folds the interface into the VM's initial configuration when the
// namespace already has one, and none of that happens without the runtime.
const canRun = (() => {
    if (process.getuid?.() !== 0) {
        return false
    }
    const info = spawnSync('docker', ['info', '-f', '{{json .Runtimes}}'], { encoding: 'utf-8' })
    return info.status === 0 && (info.stdout ?? '').includes(KATA_CLH_RUNTIME)
})()

const IMAGE = 'alpine:latest'

// Each case boots a guest kernel and image, and the restart case boots two, so the ceiling is
// far above bun's default — it is there to fail a wedged run, not to pace a healthy one
const BOOT_TIMEOUT_MS = 180_000

describe.skipIf(!canRun)('a workload started with a namespace anchor has a network', () => {

    let baseDir: string
    let provider: CloudHypervisorProvider
    let workloadId: string

    beforeEach(() => {
        baseDir = join(tmpdir(), `clh-net-${crypto.randomUUID()}`)
        mkdirSync(baseDir, { recursive: true })
        const docker = new Docker()
        provider = new CloudHypervisorProvider(join(baseDir, 'state'),
                                               docker,
                                               join(baseDir, 'data'),
                                               undefined,
                                               null,
                                               new NetnsAnchorManager(docker))
    })

    afterEach(async () => {
        try {
            await provider.destroy(workloadId)
        } catch {
            // The test that failed to start it has nothing to tear down
        }
        rmSync(baseDir, { recursive: true, force: true })
    })

    // Runs to completion so the workload's own view of its network is on stdout by the time
    // start() resolves, rather than being read out of the guest by a second command
    function reporting(script: string): Workload {
        const workload = new Workload('net-probe', IMAGE)
        workload.id = `net-${Date.now().toString(36)}`
        workload.detached = false
        workload.cmd = ['sh', '-c', script]
        workloadId = workload.id
        return workload
    }

    // Read back off the daemon rather than through the provider: what the guest wrote is the
    // observation, and the provider exposes log targets only while a workload is still running
    function output(): string {
        return spawnSync('docker', ['logs', workloadId], { encoding: 'utf-8' }).stdout ?? ''
    }

    it('gives the guest an address and a default route', async () => {
        const workload = await provider.start(
            reporting('echo "addr=$(ip -o -4 addr show eth0 | wc -l) route=$(ip -o -4 route | grep -c default)"'))

        expect(workload.exitCode).toBe(0)
        expect(output()).toContain('addr=1 route=1')
    }, BOOT_TIMEOUT_MS)

    it('keeps the address and the writable layer across a restart', async () => {
        const script = 'echo "boot=$(cat /marker 2>/dev/null || echo first) route=$(ip -o -4 route | grep -c default)"'
                       + '; echo again > /marker'
        // A non-detached run has already ended by the time start() resolves, which is the
        // STOPPED restart() requires — there is nothing left to stop
        const first = await provider.start(reporting(script))
        expect(first.status).toBe(WorkloadStatus.STOPPED)
        expect(output()).toContain('boot=first route=1')

        const restarted = await provider.restart(workloadId)

        expect(restarted.exitCode).toBe(0)
        // The second line proves both: the marker written by the first run survived, and the
        // guest still had a route after attaching to the namespace a second time
        expect(output()).toContain('boot=again route=1')
    }, BOOT_TIMEOUT_MS)
})
