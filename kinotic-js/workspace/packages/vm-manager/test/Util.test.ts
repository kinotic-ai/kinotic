import { describe, expect, it } from 'bun:test'
import { Util } from '@/internal/api/Util'

describe('Util.mustPullBeforeStart', () => {

    it('pulls a floating reference every time', () => {
        expect(Util.mustPullBeforeStart('kinoticai/workload-runner')).toBeTrue()
        expect(Util.mustPullBeforeStart('kinoticai/workload-runner:latest')).toBeTrue()
        expect(Util.mustPullBeforeStart('registry.example.com:5000/kinoticai/workload-runner')).toBeTrue()
    })

    it('trusts the node copy of a pinned reference', () => {
        expect(Util.mustPullBeforeStart('ghcr.io/kinotic-test/helios-batch:0.7.3')).toBeFalse()
        expect(Util.mustPullBeforeStart('registry.example.com:5000/kinoticai/workload-runner:5.0.0')).toBeFalse()
        expect(Util.mustPullBeforeStart('kinoticai/workload-runner@sha256-cac052b36d5471426b5b5e4bff14b57ff72de325b19dad5a84dfb58f51070ed0')).toBeFalse()
    })
})
