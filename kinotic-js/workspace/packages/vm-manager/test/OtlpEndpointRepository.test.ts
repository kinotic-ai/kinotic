import { describe, expect, it } from 'bun:test'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OtlpEndpointRepository } from '@/internal/api/telemetry/OtlpEndpointRepository'

const SIGNALS = { traces: true, metrics: true }

function dataDir(): string {
    return mkdtempSync(join(tmpdir(), 'otlp-endpoints-'))
}

describe('OtlpEndpointRepository', () => {

    it('issues every workload a port and token of its own', () => {
        const repository = new OtlpEndpointRepository(dataDir(), SIGNALS)

        const first = repository.issue('wl-1', '127.0.0.1')
        const second = repository.issue('wl-2', '127.0.0.1')

        expect(first.port).not.toBe(second.port)
        expect(first.token).not.toBe(second.token)
        expect(first.token).toMatch(/^[0-9a-f]{64}$/)
        expect(first.listenAddress).toBe('127.0.0.1')
    })

    it('answers a workload that already holds an endpoint with that endpoint', () => {
        const repository = new OtlpEndpointRepository(dataDir(), SIGNALS)

        const issued = repository.issue('wl-1', '127.0.0.1')

        expect(repository.issue('wl-1', '127.0.0.1')).toEqual(issued)
        expect(repository.find('wl-1')).toEqual(issued)
    })

    it('holds nothing for a workload that was never issued one', () => {
        expect(new OtlpEndpointRepository(dataDir(), SIGNALS).find('wl-1')).toBeNull()
    })

    it('keeps issued endpoints across vm-manager processes', () => {
        const dir = dataDir()
        const issued = new OtlpEndpointRepository(dir, SIGNALS).issue('wl-1', '172.17.0.1')

        // A guest booted by the previous process still holds this port and token
        expect(new OtlpEndpointRepository(dir, SIGNALS).find('wl-1')).toEqual(issued)
    })

    it('returns a released port to the pool', () => {
        const repository = new OtlpEndpointRepository(dataDir(), SIGNALS)
        const first = repository.issue('wl-1', '127.0.0.1')
        repository.issue('wl-2', '127.0.0.1')

        repository.release('wl-1')

        expect(repository.find('wl-1')).toBeNull()
        expect(repository.issue('wl-3', '127.0.0.1').port).toBe(first.port)
    })

    it('refuses to issue past the port range', () => {
        const repository = new OtlpEndpointRepository(dataDir(), SIGNALS)
        for (let i = 0; i < 100; i++) {
            repository.issue(`wl-${i}`, '127.0.0.1')
        }

        expect(() => repository.issue('wl-100', '127.0.0.1')).toThrow(/in use/)
    })

    it('refuses to start over a record it cannot read', () => {
        const dir = dataDir()
        writeFileSync(join(dir, 'otlp-endpoints.json'), '{not json')

        // Reissuing would hand out ports and tokens the running guests do not hold
        expect(() => new OtlpEndpointRepository(dir, SIGNALS)).toThrow(/OTLP endpoints/)
    })

    it('renders the exporter configuration a guest needs, one exporter per shipped signal', () => {
        const endpoint = { listenAddress: '127.0.0.1', port: 43180, token: 'abc123' }

        expect(new OtlpEndpointRepository(dataDir(), SIGNALS).guestEnvironment('192.168.127.254', endpoint)).toEqual({
            OTEL_EXPORTER_OTLP_ENDPOINT: 'http://192.168.127.254:43180',
            OTEL_EXPORTER_OTLP_PROTOCOL: 'http/protobuf',
            OTEL_EXPORTER_OTLP_HEADERS: 'authorization=Bearer%20abc123',
            OTEL_TRACES_EXPORTER: 'otlp',
            OTEL_METRICS_EXPORTER: 'otlp',
            OTEL_LOGS_EXPORTER: 'none',
        })
        // A signal the node does not ship is turned off, since the endpoint would refuse it
        expect(new OtlpEndpointRepository(dataDir(), { traces: true, metrics: false }).guestEnvironment('192.168.127.254', endpoint))
            .toMatchObject({ OTEL_TRACES_EXPORTER: 'otlp', OTEL_METRICS_EXPORTER: 'none' })
    })
})
