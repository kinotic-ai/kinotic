import { describe, expect, it } from 'bun:test'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { SYSTEM_LOG_TENANT } from '@kinotic-ai/os-api'
import { AlloyManager, terminateStaleAlloy } from '@/internal/api/logging/AlloyManager'
import type { LogTarget } from '@/model/LogTarget'

function target(overrides: Partial<LogTarget> = {}): LogTarget {
    return {
        workloadId: '9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c',
        vmId: 'KeUwLBZv2RFz',
        logDir: '/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c',
        organizationId: 'acme',
        applicationId: null,
        ...overrides,
    }
}

/**
 * Drives applyTargets with a binary path that cannot exist: the config file is written
 * before the spawn fails, so the written pipeline is the observable output under test.
 */
async function configFor(targets: LogTarget[]): Promise<string> {
    const dataDir = mkdtempSync(join(tmpdir(), 'alloy-config-'))
    const manager = new AlloyManager({
        lokiUrl: 'http://loki:3100',
        nodeId: 'node-1',
        dataDir,
        binaryPath: join(dataDir, 'no-such-alloy'),
        version: 'v0.0.0',
    })

    await expect(manager.applyTargets(targets)).rejects.toThrow('KINOTIC_ALLOY_PATH')
    return readFileSync(join(dataDir, 'config.alloy'), 'utf-8')
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

    it('routes a platform workload (no organization) to the system tenant', async () => {
        const config = await configFor([target({ organizationId: null })])

        expect(config).toContain(`tenant         = "${SYSTEM_LOG_TENANT}"`)
    })

    it('labels application_id only when the workload has one', async () => {
        const withApp = await configFor([target({ applicationId: 'app-7' })])
        const withoutApp = await configFor([target()])

        expect(withApp).toContain('application_id = "app-7"')
        expect(withoutApp).not.toContain('application_id')
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

describe('terminateStaleAlloy', () => {

    it('terminates the recorded process and removes the pid file', async () => {
        const orphan = spawn('sleep', ['60'])
        const pidFile = join(mkdtempSync(join(tmpdir(), 'alloy-pid-')), 'alloy.pid')
        writeFileSync(pidFile, String(orphan.pid))

        await terminateStaleAlloy(pidFile)

        expect(isAlive(orphan.pid!)).toBeFalse()
        expect(existsSync(pidFile)).toBeFalse()
    })

    it('is a no-op without a pid file', async () => {
        await terminateStaleAlloy(join(tmpdir(), 'nonexistent-dir', 'alloy.pid'))
    })

    it('tolerates a recorded process that already exited', async () => {
        const exited = spawn('true')
        await new Promise(resolve => exited.once('exit', resolve))
        const pidFile = join(mkdtempSync(join(tmpdir(), 'alloy-pid-')), 'alloy.pid')
        writeFileSync(pidFile, String(exited.pid))

        await terminateStaleAlloy(pidFile)

        expect(existsSync(pidFile)).toBeFalse()
    })
})
