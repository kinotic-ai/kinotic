import { describe, expect, it } from 'bun:test'
import { NetworkMode, PortProtocol, Workload } from '@kinotic-ai/system-api'
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

    it('rejects more volume mounts than boxlite can boot', () => {
        const w = workload()
        w.volumeMounts = [
            { hostPath: '/srv/repo', guestPath: '/workspace/repo', readOnly: true },
            { hostPath: '/srv/out', guestPath: '/workspace/out', readOnly: false },
        ]

        // With the log mount that is three volumes, which fails the VM with libkrun status=-22
        expect(() => buildBoxOptions(w, '/logs/wl-1')).toThrow('volume mount')
    })

    it('accepts the one workload volume that fits alongside the log mount', () => {
        const w = workload()
        w.volumeMounts = [{ hostPath: '/srv/repo', guestPath: '/workspace/repo', readOnly: true }]

        expect(buildBoxOptions(w, '/logs/wl-1').volumes).toHaveLength(2)
    })

    it('sends the workload network policy rather than relying on a boxlite default', () => {
        const options = buildBoxOptions(workload(), '/logs/wl-1')

        // An empty allowlist is a denial: boxlite reads a missing allowNet as no filter
        expect(options.network).toEqual({ outbound: { mode: 'enabled', allowNet: ['192.0.2.1'] } })
    })

    it('restricts egress to the hosts the workload allows', () => {
        const w = workload()
        w.network.allowedHosts = ['api.github.com']

        expect(buildBoxOptions(w, '/logs/wl-1').network)
            .toEqual({ outbound: { mode: 'enabled', allowNet: ['api.github.com'] } })
    })

    it('denies every destination when the network is disabled', () => {
        const w = workload()
        w.network.mode = NetworkMode.DISABLED

        // A disabled network leaves the VM with no interface at all, rather than one
        // filtered down to nothing
        expect(buildBoxOptions(w, '/logs/wl-1').network)
            .toEqual({ outbound: { mode: 'disabled' } })
    })

    it('drops the allowlist of a workload whose network is disabled', () => {
        const w = workload()
        w.network.mode = NetworkMode.DISABLED
        w.network.allowedHosts = ['api.github.com']

        expect(buildBoxOptions(w, '/logs/wl-1').network)
            .toEqual({ outbound: { mode: 'disabled' } })
    })

    it('denies egress to a record that carries no network policy', () => {
        const w = JSON.parse(JSON.stringify(workload())) as Workload
        delete (w as Partial<Workload>).network

        // A workload whose policy cannot be read is the case least safe to guess at, so it
        // gets the same denial an empty allowlist does rather than the boxlite default
        expect(buildBoxOptions(w, '/logs/wl-1').network)
            .toEqual({ outbound: { mode: 'enabled', allowNet: ['192.0.2.1'] } })
    })

    it('passes a multi-gigabyte rootfs through to boxlite', () => {
        const w = workload()
        w.diskSizeMb = 4096

        expect(buildBoxOptions(w, '/logs/wl-1').diskSizeGb).toBe(4)
    })

    it('rounds the workload disk size up to whole GB for the guest rootfs', () => {
        const w = workload()
        w.diskSizeMb = 512

        expect(buildBoxOptions(w, '/logs/wl-1').diskSizeGb).toBe(1)
    })

    it('leaves the boxlite rootfs default when no disk size is declared', () => {
        const w = workload()
        w.diskSizeMb = 0

        expect('diskSizeGb' in buildBoxOptions(w, '/logs/wl-1')).toBeFalse()
    })

    it('keeps image defaults by omitting undeclared entrypoint and cmd', () => {
        const options = buildBoxOptions(workload(), '/logs/wl-1')

        expect('entrypoint' in options).toBeFalse()
        expect('cmd' in options).toBeFalse()
    })

    it('maps port mappings to boxlite ports with lowercase protocols', () => {
        const w = workload()
        w.portMappings = [
            { hostPort: 8080, guestPort: 80, protocol: PortProtocol.TCP },
            { guestPort: 53, protocol: PortProtocol.UDP },
            { guestPort: 443 },
        ]

        const options = buildBoxOptions(w, '/logs/wl-1')

        // boxlite recognizes only lowercase 'udp'; anything else silently means tcp
        expect(options.ports).toEqual([
            { hostPort: 8080, guestPort: 80, protocol: 'tcp' },
            { guestPort: 53, protocol: 'udp' },
            { guestPort: 443 },
        ])
    })

    it('defaults to detached when deserialized JSON omits the field', () => {
        const w = JSON.parse(JSON.stringify(workload())) as Workload
        delete (w as Partial<Workload>).detached

        const options = buildBoxOptions(w, '/logs/wl-1')

        expect(options.detach).toBeTrue()
    })

    it('suppresses the image CMD when an entrypoint is declared without a cmd', () => {
        const w = workload()
        w.entrypoint = ['sleep', '600']

        const options = buildBoxOptions(w, '/logs/wl-1')

        expect(options.entrypoint).toEqual(['sleep', '600'])
        expect(options.cmd).toEqual([])
    })

    it('rejects a host interface binding boxlite cannot honor', () => {
        const w = workload()
        w.portMappings = [{ hostPort: 8080, guestPort: 80, hostIp: '127.0.0.1' }]

        expect(() => buildBoxOptions(w, '/logs/wl-1')).toThrow('hostIp')
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
