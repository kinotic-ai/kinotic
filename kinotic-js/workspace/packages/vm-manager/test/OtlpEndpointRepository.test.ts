import { describe, expect, it } from 'bun:test'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OtlpEndpointRepository } from '@/internal/api/telemetry/OtlpEndpointRepository'
import type { OtlpSignal } from '@/internal/api/model/OtlpSignal'

const SIGNALS: OtlpSignal[] = ['traces', 'metrics']

function dataDir(): string {
    return mkdtempSync(join(tmpdir(), 'otlp-endpoints-'))
}

describe('OtlpEndpointRepository', () => {

    it('issues every workload a port and token of its own', () => {
        const repository = new OtlpEndpointRepository(dataDir())

        const first = repository.issue('wl-1', '127.0.0.1', SIGNALS)
        const second = repository.issue('wl-2', '127.0.0.1', SIGNALS)

        expect(first.port).not.toBe(second.port)
        expect(first.token).not.toBe(second.token)
        expect(first.token).toMatch(/^[0-9a-f]{64}$/)
        expect(first.listenAddress).toBe('127.0.0.1')
        expect(first.signals).toEqual(SIGNALS)
    })

    it('answers a workload that already holds an endpoint with that endpoint', () => {
        const repository = new OtlpEndpointRepository(dataDir())

        const issued = repository.issue('wl-1', '127.0.0.1', SIGNALS)

        expect(repository.issue('wl-1', '127.0.0.1', SIGNALS)).toEqual(issued)
        expect(repository.find('wl-1')).toEqual(issued)
    })

    it('holds nothing for a workload that was never issued one', () => {
        expect(new OtlpEndpointRepository(dataDir()).find('wl-1')).toBeNull()
    })

    it('keeps issued endpoints across vm-manager processes', () => {
        const dir = dataDir()
        const issued = new OtlpEndpointRepository(dir).issue('wl-1', '172.17.0.1', SIGNALS)

        // A guest booted by the previous process still holds this port and token
        expect(new OtlpEndpointRepository(dir).find('wl-1')).toEqual(issued)
    })

    it('returns a released port to the pool', () => {
        const repository = new OtlpEndpointRepository(dataDir())
        const first = repository.issue('wl-1', '127.0.0.1', SIGNALS)
        repository.issue('wl-2', '127.0.0.1', SIGNALS)

        repository.release('wl-1')

        expect(repository.find('wl-1')).toBeNull()
        expect(repository.issue('wl-3', '127.0.0.1', SIGNALS).port).toBe(first.port)
    })

    it('refuses to issue past the port range', () => {
        const repository = new OtlpEndpointRepository(dataDir())
        for (let i = 0; i < 100; i++) {
            repository.issue(`wl-${i}`, '127.0.0.1', SIGNALS)
        }

        expect(() => repository.issue('wl-100', '127.0.0.1', SIGNALS)).toThrow(/in use/)
    })

    it('releases the endpoints of workloads the node no longer holds', () => {
        const repository = new OtlpEndpointRepository(dataDir())
        const kept = repository.issue('wl-1', '127.0.0.1', SIGNALS)
        repository.issue('wl-2', '127.0.0.1', SIGNALS)

        repository.reconcile(new Set(['wl-1']))

        expect(repository.find('wl-1')).toEqual(kept)
        expect(repository.find('wl-2')).toBeNull()
    })

    it('refuses to start over a record it cannot read', () => {
        const dir = dataDir()
        writeFileSync(join(dir, 'otlp-endpoints.json'), '{not json')

        // Reissuing would hand out ports and tokens the running guests do not hold
        expect(() => new OtlpEndpointRepository(dir)).toThrow(/OTLP endpoints/)
    })

})
