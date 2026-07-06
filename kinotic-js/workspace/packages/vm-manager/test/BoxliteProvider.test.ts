import { describe, expect, it } from 'bun:test'
import { Workload } from '@kinotic-ai/os-api'
import { GUEST_LOG_DIR, buildBoxOptions } from '@/internal/api/providers/BoxliteProvider'

function workload(): Workload {
    const w = new Workload('build', 'alpine:latest')
    w.id = 'wl-1'
    return w
}

describe('buildBoxOptions', () => {

    it('always mounts the host log directory at the guest log dir', () => {
        const w = workload()
        w.volumeMounts = [{ hostPath: '/data', guestPath: '/app/data', readOnly: true }]

        const options = buildBoxOptions(w, '/logs/wl-1')

        expect(options.volumes).toEqual([
            { hostPath: '/data', guestPath: '/app/data', readOnly: true },
            { hostPath: '/logs/wl-1', guestPath: GUEST_LOG_DIR },
        ])
    })

    it('keeps image defaults by omitting undeclared entrypoint and cmd', () => {
        const options = buildBoxOptions(workload(), '/logs/wl-1')

        expect('entrypoint' in options).toBeFalse()
        expect('cmd' in options).toBeFalse()
    })

    it('passes declared entrypoint and cmd through unmodified', () => {
        const w = workload()
        w.entrypoint = ['/bin/run-build', '--verbose']
        w.cmd = ['release']

        const options = buildBoxOptions(w, '/logs/wl-1')

        expect(options.entrypoint).toEqual(['/bin/run-build', '--verbose'])
        expect(options.cmd).toEqual(['release'])
    })
})
