import { afterAll, describe, expect, it } from 'bun:test'
import { existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Workload, WorkloadStatus } from '@kinotic-ai/os-api'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'

// Real boxlite VMs need virtualization: Hypervisor.framework on macOS, /dev/kvm on Linux.
// The boxlite runtime aborts the whole process on unsupported hosts, so the gate must be
// checked without touching boxlite.
const canRunBoxlite = process.platform === 'darwin' || existsSync('/dev/kvm')
const itVm = canRunBoxlite ? it : it.skip

// First run may pull the alpine image; boots take seconds each
const VM_TIMEOUT = 240_000

describe('BoxliteProvider recovery and restart', () => {

    const base = mkdtempSync(join(tmpdir(), 'vm-manager-recovery-'))
    const logsDir = join(base, 'logs')
    const stateDir = join(base, 'state')
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

    function longRunningWorkload(): Workload {
        const w = new Workload('recovery-test', 'alpine:latest')
        w.entrypoint = ['sleep', '600']
        w.organizationId = 'acme'
        return w
    }

    itVm('recovers workloads across provider generations and restarts them in place', async () => {
        // Every status transition in any generation lands here via the provider listener
        const reported: WorkloadStatus[] = []
        const onStatusChanged = (w: Workload) => reported.push(w.status)

        // Generation 1: start a detached workload, then abandon the provider without
        // stopping anything — the VM keeps running, modelling a crashed vm-manager
        const first = new BoxliteProvider(logsDir, stateDir, onStatusChanged)
        const started = await first.start(longRunningWorkload())
        startedIds.push(started.id!)
        expect(started.status).toBe(WorkloadStatus.RUNNING)
        const [target] = await first.listLogTargets()
        expect(target).toBeDefined()

        // Generation 2: a fresh provider over the same dirs models the restarted process
        const second = new BoxliteProvider(logsDir, stateDir, onStatusChanged)
        await second.recover()

        const recovered = await second.getWorkload(started.id!)
        expect(recovered.status).toBe(WorkloadStatus.RUNNING)
        expect(recovered.organizationId).toBe('acme')
        expect(await second.listLogTargets()).toEqual([{
            workloadId: started.id!,
            vmId: target!.vmId,
            logDir: target!.logDir,
            organizationId: 'acme',
            applicationId: null,
        }])

        // The reattached handle really controls the box
        await second.stop(started.id!)

        // Generation 3: recovery after the stop — workload present but dormant
        const third = new BoxliteProvider(logsDir, stateDir, onStatusChanged)
        await third.recover()
        expect((await third.getWorkload(started.id!)).status).toBe(WorkloadStatus.STOPPED)
        expect(await third.listLogTargets()).toEqual([])

        // Restart in place: same VM, disk intact, shipping resumes
        const restarted = await third.restart(started.id!)
        expect(restarted.status).toBe(WorkloadStatus.RUNNING)
        const [again] = await third.listLogTargets()
        expect(again!.vmId).toBe(target!.vmId)

        await third.destroy(started.id!)
        expect(existsSync(join(stateDir, `${started.id}.json`))).toBeFalse()

        // The full arc as the status listener saw it: gen-1 start, gen-2 reattach and
        // stop, gen-3 restart. Recovery of the already-STOPPED workload reports nothing.
        expect(reported).toEqual([
            WorkloadStatus.STARTING, WorkloadStatus.RUNNING,
            WorkloadStatus.RUNNING,
            WorkloadStatus.STOPPING, WorkloadStatus.STOPPED,
            WorkloadStatus.STARTING, WorkloadStatus.RUNNING,
        ])
    }, VM_TIMEOUT)

    itVm('rejects restarting a workload that is not stopped', async () => {
        const provider = new BoxliteProvider(logsDir, stateDir)
        const started = await provider.start(longRunningWorkload())
        startedIds.push(started.id!)

        expect(provider.restart(started.id!)).rejects.toThrow('is not stopped')

        await provider.destroy(started.id!)
    }, VM_TIMEOUT)

    itVm('rejects restarting a workload whose VM was discarded by autoRemove', async () => {
        const provider = new BoxliteProvider(logsDir, stateDir)
        const workload = longRunningWorkload()
        workload.autoRemove = true
        const started = await provider.start(workload)
        startedIds.push(started.id!)

        await provider.stop(started.id!)

        expect(provider.restart(started.id!)).rejects.toThrow('cannot be restarted')

        await provider.destroy(started.id!)
    }, VM_TIMEOUT)
})
