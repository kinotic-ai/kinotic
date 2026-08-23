import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { existsSync, mkdirSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import type Docker from 'dockerode'
import { Workload, WorkloadStatus } from '@kinotic-ai/system-api'
import { CloudHypervisorProvider } from '@/internal/api/providers/CloudHypervisorProvider'

// Mount validation runs before the provider touches Docker, so a stub whose first call
// throws this sentinel both proves validation passed and stops the start there
const DOCKER_SENTINEL = new Error('reached docker')
const docker = { listImages: () => { throw DOCKER_SENTINEL } } as unknown as Docker

describe('CloudHypervisorProvider volume mount preparation', () => {

    let baseDir: string
    let provider: CloudHypervisorProvider

    beforeEach(() => {
        baseDir = join(tmpdir(), `clh-mounts-${crypto.randomUUID()}`)
        provider = new CloudHypervisorProvider(join(baseDir, 'state'), docker, join(baseDir, 'data'))
    })

    afterEach(() => {
        rmSync(baseDir, { recursive: true, force: true })
    })

    function workload(hostPath: string, readOnly = false): Workload {
        const w = new Workload('checkout', 'oven/bun:latest')
        w.id = 'wl-1'
        w.volumeMounts = [{ hostPath, guestPath: '/workspace', readOnly }]
        return w
    }

    it('rejects a mount outside the workload data directory', async () => {
        const w = workload('/etc')

        await expect(provider.start(w)).rejects.toThrow(/inside the workload data directory/)
        expect(w.status).toBe(WorkloadStatus.FAILED)
    })

    it('rejects a mount escaping the data directory through traversal', async () => {
        await expect(provider.start(workload(join(baseDir, 'data', '..', 'escaped'))))
            .rejects.toThrow(/inside the workload data directory/)
    })

    it('rejects a relative mount path', async () => {
        await expect(provider.start(workload('projects/p1')))
            .rejects.toThrow(/inside the workload data directory/)
    })

    it('rejects a mount of the data directory itself', async () => {
        await expect(provider.start(workload(join(baseDir, 'data'))))
            .rejects.toThrow(/inside the workload data directory/)
    })

    it('rejects a read-only mount of a directory that does not exist', async () => {
        await expect(provider.start(workload(join(baseDir, 'data', 'projects', 'p1'), true)))
            .rejects.toThrow(/does not exist on this node/)
    })

    it('creates a missing writable mount directory before starting', async () => {
        const hostPath = join(baseDir, 'data', 'projects', 'p1')

        await expect(provider.start(workload(hostPath))).rejects.toThrow(DOCKER_SENTINEL.message)
        expect(existsSync(hostPath)).toBe(true)
    })

    it('accepts a read-only mount of an existing directory', async () => {
        const hostPath = join(baseDir, 'data', 'projects', 'p1')
        mkdirSync(hostPath, { recursive: true })

        await expect(provider.start(workload(hostPath, true))).rejects.toThrow(DOCKER_SENTINEL.message)
    })
})
