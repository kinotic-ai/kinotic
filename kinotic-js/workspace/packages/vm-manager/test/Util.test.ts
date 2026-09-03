import { describe, expect, it } from 'bun:test'

// Resolution talks to Docker Hub; without a route to it the cases below would only test the network
const online = await fetch('https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/alpine:pull')
    .then(r => r.ok).catch(() => false)
const itOnline = online ? it : it.skip
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

describe('Util.pinImageReference', () => {

    itOnline('pins a floating tag to the digest the registry serves', async () => {
        const pinned = await Util.pinImageReference('alpine')
        expect(pinned).toMatch(/^alpine@sha256:[0-9a-f]{64}$/)
        expect(await Util.pinImageReference('alpine:latest')).toBe(pinned)
    })

    itOnline('keeps the registry host of a non-Hub image', async () => {
        expect(await Util.pinImageReference('ghcr.io/actions/actions-runner:latest')).toMatch(/^ghcr\.io\/actions\/actions-runner@sha256:/)
    })

    it('returns a digest reference unchanged', async () => {
        const ref = 'kinoticai/workload-runner@sha256:b4dbb2d4e33fe01761e0c49a670f237a793b72e6cde1cbd0afa6ebe7958ba54a'
        expect(await Util.pinImageReference(ref)).toBe(ref)
    })
})
