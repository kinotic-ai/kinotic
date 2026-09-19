import { afterAll, describe, expect, it } from 'bun:test'
import { accessSync, constants, existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Workload, WorkloadStatus } from '@kinotic-ai/management-api'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'
import { LogFormat } from '@/internal/api/model/LogFormat'

// Real boxlite VMs need virtualization: Hypervisor.framework on macOS, /dev/kvm on Linux.
// The boxlite runtime aborts the whole process on unsupported hosts, so the gate must be
// checked without touching boxlite — and it must check rw access, not existence: GitHub
// Actions runners have /dev/kvm but deny the runner user permission to open it.
function kvmUsable(): boolean {
    try {
        accessSync('/dev/kvm', constants.R_OK | constants.W_OK)
        return true
    } catch {
        return false
    }
}
const canRunBoxlite = process.platform === 'darwin' || kvmUsable()
const itVm = canRunBoxlite ? it : it.skip

// First run may pull the alpine image; boots take seconds each
const VM_TIMEOUT = 240_000

describe('BoxliteProvider recovery and restart', () => {

    const base = mkdtempSync(join(tmpdir(), 'vm-manager-recovery-'))
    const boxliteHome = join(base, 'boxlite')
    const logsDir = join(base, 'logs')
    const stateDir = join(base, 'state')
    const dataDir = join(base, 'data')
    const startedIds: string[] = []

    afterAll(async () => {
        // Force-remove any boxes a failed test left behind before dropping the temp dirs
        if (canRunBoxlite && startedIds.length > 0) {
            const { getJsBoxlite } = await import('@boxlite-ai/boxlite')
            const runtime = getJsBoxlite().withDefaultConfig()
            for (const id of startedIds) {
                try {
                    if (await runtime.getInfo(id)) {
                        await runtime.remove(id, true)
                    }
                } catch {
                    // box already gone
                }
            }
        }
        rmSync(base, { recursive: true, force: true })
    })

    // Pinned by digest (multi-arch manifest list, alpine 3.22): CI boots this image as a VM
    // guest in the same job that holds publishing secrets, so a mutable tag must not be able
    // to swap the guest code underneath us.
    const GUEST_IMAGE = 'alpine:3.22@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce'

    function longRunningWorkload(): Workload {
        const w = new Workload('recovery-test', GUEST_IMAGE)
        w.entrypoint = ['sleep', '600']
        w.organizationId = 'acme'
        return w
    }

    itVm('recovers workloads across provider generations', async () => {
        // Every status transition in any generation lands here via the provider listener
        const reported: WorkloadStatus[] = []
        const onStatusChanged = (w: Workload) => reported.push(w.status)

        // Generation 1: start a detached workload, then abandon the provider without
        // stopping anything — the VM keeps running, modelling a crashed vm-manager
        const first = new BoxliteProvider(boxliteHome, logsDir, stateDir, dataDir, onStatusChanged)
        const started = await first.start(longRunningWorkload())
        startedIds.push(started.id!)
        expect(started.status).toBe(WorkloadStatus.RUNNING)
        const [target] = await first.listTelemetryTargets()
        expect(target).toBeDefined()

        // Generation 2: a fresh provider over the same dirs models the restarted process
        const second = new BoxliteProvider(boxliteHome, logsDir, stateDir, dataDir, onStatusChanged)
        await second.recover()

        const recovered = await second.getWorkload(started.id!)
        expect(recovered.status).toBe(WorkloadStatus.RUNNING)
        expect(recovered.organizationId).toBe('acme')
        expect(await second.listTelemetryTargets()).toEqual([{
            workloadId: started.id!,
            vmId: target!.vmId,
            logPath: target!.logPath,
            format: LogFormat.PLAIN,
            otlp: null,
            organizationId: 'acme',
            applicationId: null,
        }])

        // The reattached handle really controls the box
        await second.stop(started.id!)

        // Generation 3: recovery after the stop — workload present but dormant, its log
        // files still shipped until destroy
        const third = new BoxliteProvider(boxliteHome, logsDir, stateDir, dataDir, onStatusChanged)
        await third.recover()
        expect((await third.getWorkload(started.id!)).status).toBe(WorkloadStatus.STOPPED)
        const [dormant] = await third.listTelemetryTargets()
        expect(dormant!.vmId).toBe(target!.vmId)

        await third.destroy(started.id!)
        expect(existsSync(join(stateDir, `${started.id}.json`))).toBeFalse()
        // a workload the provider no longer knows is already gone
        await third.destroy(started.id!)

        // The full arc as the status listener saw it: gen-1 start, gen-2 reattach and
        // stop. Recovery of the already-STOPPED workload reports nothing.
        expect(reported).toEqual([
            WorkloadStatus.STARTING, WorkloadStatus.RUNNING,
            WorkloadStatus.RUNNING,
            WorkloadStatus.STOPPING, WorkloadStatus.STOPPED,
        ])
    }, VM_TIMEOUT)

})
