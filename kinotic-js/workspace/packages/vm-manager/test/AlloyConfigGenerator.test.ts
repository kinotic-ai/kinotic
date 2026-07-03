import { describe, expect, it } from 'bun:test'
import { SYSTEM_LOG_TENANT } from '@kinotic-ai/os-api'
import { generateAlloyConfig } from '@/internal/api/logging/AlloyConfigGenerator'
import type { LogTarget } from '@/model/LogTarget'

const OPTIONS = { lokiUrl: 'http://loki:3100', nodeId: 'node-1' }

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

describe('generateAlloyConfig', () => {

    it('routes a workload with an organization to that org tenant', () => {
        const config = generateAlloyConfig([target()], OPTIONS)

        expect(config).toContain('tenant         = "acme"')
        expect(config).toContain('workload_id    = "9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c"')
        expect(config).toContain('vm_id          = "KeUwLBZv2RFz"')
        expect(config).toContain('node_id        = "node-1"')
        expect(config).toContain('__path__       = "/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c/*.log"')
    })

    it('routes a platform workload (no organization) to the system tenant', () => {
        const config = generateAlloyConfig([target({ organizationId: null })], OPTIONS)

        expect(config).toContain(`tenant         = "${SYSTEM_LOG_TENANT}"`)
    })

    it('labels application_id only when the workload has one', () => {
        const withApp = generateAlloyConfig([target({ applicationId: 'app-7' })], OPTIONS)
        const withoutApp = generateAlloyConfig([target()], OPTIONS)

        expect(withApp).toContain('application_id = "app-7"')
        expect(withoutApp).not.toContain('application_id')
    })

    it('derives valid component names from UUID workload ids', () => {
        const config = generateAlloyConfig([target()], OPTIONS)

        expect(config).toContain('local.file_match "wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c"')
        expect(config).toContain('targets    = local.file_match.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.targets')
    })

    it('routes the tenant via a transient label that is dropped before push', () => {
        const config = generateAlloyConfig([target()], OPTIONS)

        expect(config).toContain('stage.tenant')
        expect(config).toContain('source = "tenant"')
        expect(config).toContain('values = ["tenant"]')
    })

    it('renders write and process stages even with no targets', () => {
        const config = generateAlloyConfig([], OPTIONS)

        expect(config).toContain('loki.write "default"')
        expect(config).toContain('url = "http://loki:3100/loki/api/v1/push"')
        expect(config).not.toContain('loki.source.file')
    })
})
