import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'
import { createHmac } from 'node:crypto'

const PUBLISH = join(import.meta.dir, '..', 'src', 'publish-ui.ts')

/** Azurite's well-known development account, reached where AZURITE_BLOB_ENDPOINT points or on its default port. */
const ACCOUNT = 'devstoreaccount1'
const ACCOUNT_KEY = 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=='
const ENDPOINT = process.env.AZURITE_BLOB_ENDPOINT ?? `http://127.0.0.1:10000/${ACCOUNT}`
const CONTAINER = 'sites'
const SAS_VERSION = '2020-12-06'

function sharedKeyHeaders(method: string, path: string, contentLength: number = 0, extra: Record<string, string> = {}): Record<string, string> {
    const date = new Date().toUTCString()
    const headers: Record<string, string> = { 'x-ms-date': date, 'x-ms-version': SAS_VERSION, ...extra }
    const canonicalHeaders = Object.keys(headers)
        .filter(name => name.startsWith('x-ms-'))
        .sort()
        .map(name => `${name}:${headers[name]}`)
        .join('\n')
    const [resource, query = ''] = path.split('?')
    const canonicalQuery = query.split('&').filter(Boolean).sort()
        .map(pair => { const [k, v] = pair.split('='); return `${k!.toLowerCase()}:${decodeURIComponent(v ?? '')}` })
        .join('\n')
    const canonicalResource = `/${ACCOUNT}${resource}${canonicalQuery ? '\n' + canonicalQuery : ''}`
    const stringToSign = [method, '', '', contentLength ? String(contentLength) : '', '', '', '', '', '', '', '', '',
                          canonicalHeaders, canonicalResource].join('\n')
    const signature = createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
    headers.Authorization = `SharedKey ${ACCOUNT}:${signature}`
    return headers
}

/** A container SAS allowing create and write for an hour, signed the way the platform signs upload URLs. */
function containerSas(): string {
    const start = new Date(Date.now() - 5 * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z')
    const expiry = new Date(Date.now() + 60 * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z')
    const permissions = 'cw'
    const stringToSign = [permissions, start, expiry, `/blob/${ACCOUNT}/${CONTAINER}`, '', '', 'https,http', SAS_VERSION,
                          'c', '', '', '', '', '', '', ''].join('\n')
    const signature = createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
    const query = new URLSearchParams({ sp: permissions, st: start, se: expiry, spr: 'https,http', sv: SAS_VERSION, sr: 'c', sig: signature })
    return query.toString()
}

async function azuriteUp(): Promise<boolean> {
    try {
        const response = await fetch(`${ENDPOINT}?comp=list`, { headers: sharedKeyHeaders('GET', '/?comp=list') })
        return response.ok
    } catch {
        return false
    }
}

async function readBlob(path: string): Promise<Response> {
    return fetch(`${ENDPOINT}/${CONTAINER}/${path}`, { headers: sharedKeyHeaders('GET', `/${CONTAINER}/${path}`) })
}

const azurite = await azuriteUp()

describe.skipIf(!azurite)('publish-ui entrypoint (against Azurite)', () => {

    let workspaceDir: string

    beforeEach(async () => {
        workspaceDir = join(tmpdir(), `workload-runner-publish-${crypto.randomUUID()}`)
        const write = (path: string, content: string) => {
            mkdirSync(dirname(join(workspaceDir, path)), { recursive: true })
            writeFileSync(join(workspaceDir, path), content)
        }
        write('package.json', '{"name": "fixture"}')
        write('packages/ui/admin/package.json', '{"name": "@fixture/admin", "scripts": {"build": "true"}}')
        write('packages/ui/admin/dist/index.html', '<html>admin</html>')
        write('packages/ui/admin/dist/assets/app.js', 'console.log("admin")')
        write('packages/ui/admin/dist/assets/deep/style.css', 'body{}')
        const created = await fetch(`${ENDPOINT}/${CONTAINER}?restype=container`,
                                    { method: 'PUT', headers: sharedKeyHeaders('PUT', `/${CONTAINER}?restype=container`) })
        expect([201, 409]).toContain(created.status)
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    it('uploads the assets under the commit, then version.json, then index.html, with their cache policies', async () => {
        const sha = 'a'.repeat(40)
        const uploadUrl = `${ENDPOINT}/${CONTAINER}/prod/shop/ui?${containerSas()}`

        const result = spawnSync('bun', [PUBLISH], {
            env: { ...process.env, KINOTIC_UI_UPLOAD_URL: uploadUrl, KINOTIC_UI_COMMIT: sha, KINOTIC_WORKSPACE_DIR: workspaceDir },
            encoding: 'utf-8',
        })

        expect(result.stderr).toBe('')
        expect(result.status).toBe(0)

        const asset = await readBlob(`prod/shop/ui/admin/${sha}/assets/deep/style.css`)
        expect(asset.status).toBe(200)
        expect(await asset.text()).toBe('body{}')
        expect(asset.headers.get('cache-control')).toBe('public, max-age=31536000, immutable')
        expect(asset.headers.get('content-type')).toBe('text/css')

        const version = await readBlob('prod/shop/ui/admin/version.json')
        expect(await version.json()).toEqual({ commitSha: sha })
        expect(version.headers.get('cache-control')).toBe('no-cache')

        const index = await readBlob('prod/shop/ui/admin/index.html')
        expect(await index.text()).toBe('<html>admin</html>')
        expect(index.headers.get('cache-control')).toBe('no-cache')
        expect(index.headers.get('content-type')).toContain('text/html')
    }, 60_000)

    it('fails when a UI was not built', async () => {
        rmSync(join(workspaceDir, 'packages', 'ui', 'admin', 'dist'), { recursive: true })

        const result = spawnSync('bun', [PUBLISH], {
            env: { ...process.env, KINOTIC_UI_UPLOAD_URL: `${ENDPOINT}/${CONTAINER}/prod/shop/ui?${containerSas()}`,
                   KINOTIC_UI_COMMIT: 'b'.repeat(40), KINOTIC_WORKSPACE_DIR: workspaceDir },
            encoding: 'utf-8',
        })

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('packages/ui/admin')
    }, 60_000)
})
