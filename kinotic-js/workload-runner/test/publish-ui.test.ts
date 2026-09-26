import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'
import { azuriteUp, containerSas, createContainer, ENDPOINT, readBlob, writeBlob } from './azurite.ts'

const PUBLISH = join(import.meta.dir, '..', 'src', 'publish-ui.ts')

const CONTAINER = 'sites'

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
        write('packages/ui/admin/dist/favicon.ico', 'icon')
        await createContainer(CONTAINER)
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    it('uploads dist as it is into the site\'s directory, stamped with the commit, then version.json, then index.html, with their cache policies', async () => {
        const sha = 'a'.repeat(40)
        // one upload URL per UI, each naming the site's directory
        const uploadUrls = JSON.stringify({ admin: `${ENDPOINT}/${CONTAINER}/admin.apps.kinotic.test?${containerSas(CONTAINER)}` })

        // a file an earlier publish left behind, stamped with its commit
        await writeBlob(CONTAINER, 'admin.apps.kinotic.test/assets/old.js', '// stale', 'b'.repeat(40))

        const result = spawnSync('bun', [PUBLISH], {
            env: { ...process.env, KINOTIC_UI_UPLOAD_URLS: uploadUrls, KINOTIC_UI_COMMIT: sha, KINOTIC_WORKSPACE_DIR: workspaceDir },
            encoding: 'utf-8',
        })

        expect(result.stderr).toBe('')
        expect(result.status).toBe(0)

        const asset = await readBlob(CONTAINER, 'admin.apps.kinotic.test/assets/deep/style.css')
        expect(asset.status).toBe(200)
        expect(await asset.text()).toBe('body{}')
        expect(asset.headers.get('cache-control')).toBe('public, max-age=31536000, immutable')
        expect(asset.headers.get('content-type')).toContain('text/css')
        expect(asset.headers.get('x-ms-meta-commit')).toBe(sha)

        const icon = await readBlob(CONTAINER, 'admin.apps.kinotic.test/favicon.ico')
        expect(await icon.text()).toBe('icon')
        expect(icon.headers.get('cache-control')).toBe('no-cache')

        const version = await readBlob(CONTAINER, 'admin.apps.kinotic.test/version.json')
        expect(await version.json()).toEqual({ commitSha: sha })
        expect(version.headers.get('cache-control')).toBe('no-cache')

        const index = await readBlob(CONTAINER, 'admin.apps.kinotic.test/index.html')
        expect(await index.text()).toBe('<html>admin</html>')
        expect(index.headers.get('cache-control')).toBe('no-cache')
        expect(index.headers.get('content-type')).toContain('text/html')
        expect(index.headers.get('x-ms-meta-commit')).toBe(sha)

        expect((await readBlob(CONTAINER, 'admin.apps.kinotic.test/assets/old.js')).status).toBe(404)
    }, 60_000)

    it('fails when a UI was not built', async () => {
        rmSync(join(workspaceDir, 'packages', 'ui', 'admin', 'dist'), { recursive: true })

        const result = spawnSync('bun', [PUBLISH], {
            env: { ...process.env, KINOTIC_UI_UPLOAD_URLS: JSON.stringify({ admin: `${ENDPOINT}/${CONTAINER}/admin.apps.kinotic.test?${containerSas(CONTAINER)}` }),
                   KINOTIC_UI_COMMIT: 'b'.repeat(40), KINOTIC_WORKSPACE_DIR: workspaceDir },
            encoding: 'utf-8',
        })

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('packages/ui/admin')
    }, 60_000)
})
