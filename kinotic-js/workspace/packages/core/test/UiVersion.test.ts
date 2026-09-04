import { describe, it, expect, afterEach } from 'vitest'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { checkUiVersion } from '../src/api/UiVersionUtil'

// A local server standing in for a site, no gateway needed, so this runs regardless of USE_GATEWAY_DOCKER.
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('checkUiVersion', () => {

        let server: Server | null = null

        afterEach(() => new Promise<void>(resolve => {
            if (server) {
                server.close(() => resolve())
                server = null
            } else {
                resolve()
            }
        }))

        async function site(status: number, body: string): Promise<string> {
            const listening = createServer((req, res) => {
                res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' })
                res.end(body)
            })
            server = listening
            await new Promise<void>(resolve => listening.listen(0, '127.0.0.1', resolve))
            return `http://127.0.0.1:${(listening.address() as AddressInfo).port}/version.json`
        }

        it('is not stale while the site serves the built commit', async () => {
            const url = await site(200, JSON.stringify({ commitSha: 'abc123' }))
            expect(await checkUiVersion('abc123', url)).toEqual({ stale: false, servedCommit: 'abc123' })
        })

        it('is stale once the site serves another commit', async () => {
            const url = await site(200, JSON.stringify({ commitSha: 'def456' }))
            expect(await checkUiVersion('abc123', url)).toEqual({ stale: true, servedCommit: 'def456' })
        })

        it('is not stale when the site has no version to read', async () => {
            const url = await site(404, 'not found')
            expect(await checkUiVersion('abc123', url)).toEqual({ stale: false, servedCommit: null })
        })

        it('is not stale when the version is not what a site publishes', async () => {
            const url = await site(200, '<html>maintenance</html>')
            expect(await checkUiVersion('abc123', url)).toEqual({ stale: false, servedCommit: null })
        })

        it('is not stale when the site cannot be reached', async () => {
            const url = await site(200, '{}')
            await new Promise<void>(resolve => server!.close(() => resolve()))
            server = null
            expect(await checkUiVersion('abc123', url)).toEqual({ stale: false, servedCommit: null })
        })

        it('rejects a missing built commit', async () => {
            await expect(checkUiVersion('')).rejects.toThrow()
        })

    })
  })
})
