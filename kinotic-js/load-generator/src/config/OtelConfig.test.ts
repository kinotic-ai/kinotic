import {afterEach, describe, expect, it} from 'vitest'
import {OtelConfig, OtelExporterType} from '@/config/OtelConfig.js'

describe('OtelConfig.fromEnv', () => {
    const original = process.env.OTEL_EXPORTER_TYPE

    afterEach(() => {
        if (original === undefined) {
            delete process.env.OTEL_EXPORTER_TYPE
        } else {
            process.env.OTEL_EXPORTER_TYPE = original
        }
    })

    it('defaults to the console exporter', () => {
        delete process.env.OTEL_EXPORTER_TYPE
        expect(OtelConfig.fromEnv().exporterType).toBe(OtelExporterType.CONSOLE)
    })

    it('accepts every exporter type by name', () => {
        for (const type of Object.values(OtelExporterType)) {
            process.env.OTEL_EXPORTER_TYPE = type
            expect(OtelConfig.fromEnv().exporterType).toBe(type)
        }
    })

    it('rejects an exporter type it does not know instead of falling back', () => {
        process.env.OTEL_EXPORTER_TYPE = 'NOOP'
        expect(() => OtelConfig.fromEnv()).toThrow(/OTEL_EXPORTER_TYPE/)
    })
})
