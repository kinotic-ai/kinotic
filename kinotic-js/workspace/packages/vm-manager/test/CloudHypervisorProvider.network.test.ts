import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import type Docker from 'dockerode'
import type { ContainerCreateOptions } from 'dockerode'
import { NetworkMode, NetworkPolicy, Workload } from '@kinotic-ai/management-api'
import { EgressPolicyManager } from '@/internal/api/EgressPolicyManager'
import { CloudHypervisorProvider } from '@/internal/api/providers/CloudHypervisorProvider'

// Pinned by digest, which the provider never pulls once the daemon reports it present
const IMAGE = `oven/bun@sha256:${'0'.repeat(64)}`
// The network decision is made by the time the container is created, so a stub that records
// the create options and throws this sentinel both captures it and stops the start there
const CREATE_SENTINEL = new Error('reached create')

// The marker is written by node provisioning, so a developer machine never has one; a
// provisioned node takes the enforcing path these cases do not cover
describe.skipIf(new EgressPolicyManager().enforces())('CloudHypervisorProvider network on a node without default-deny', () => {

    let baseDir: string
    let created: ContainerCreateOptions | null
    let provider: CloudHypervisorProvider

    beforeEach(() => {
        baseDir = join(tmpdir(), `clh-network-${crypto.randomUUID()}`)
        created = null
        const docker = {
            listImages: async () => [{}],
            getContainer: () => ({ remove: async () => {} }),
            createContainer: async (options: ContainerCreateOptions) => {
                created = options
                throw CREATE_SENTINEL
            },
        } as unknown as Docker
        provider = new CloudHypervisorProvider(join(baseDir, 'state'), docker, join(baseDir, 'data'))
    })

    afterEach(() => {
        rmSync(baseDir, { recursive: true, force: true })
    })

    function workload(mode: NetworkMode, allowedHosts: string[]): Workload {
        const w = new Workload('checkout', IMAGE)
        w.id = 'wl-1'
        w.network = new NetworkPolicy()
        w.network.mode = mode
        w.network.allowedHosts = allowedHosts
        return w
    }

    it('creates a workload whose policy grants nothing with no network, where the bridge would withhold nothing', async () => {
        await expect(provider.start(workload(NetworkMode.ENABLED, []))).rejects.toThrow(CREATE_SENTINEL.message)
        expect(created?.HostConfig?.NetworkMode).toBe('none')
    })

    it('refuses a workload declaring destinations the node cannot grant', async () => {
        await expect(provider.start(workload(NetworkMode.ENABLED, ['github.com'])))
            .rejects.toThrow(/does not deny workload egress by default/)
        expect(created).toBeNull()
    })

    it('keeps a workload that disabled its network off it', async () => {
        await expect(provider.start(workload(NetworkMode.DISABLED, []))).rejects.toThrow(CREATE_SENTINEL.message)
        expect(created?.HostConfig?.NetworkMode).toBe('none')
    })
})
