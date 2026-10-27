import { describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { azuriteUp, putBlob, readBlob, siteUrl } from './azurite.ts'

const REMOVE = join(import.meta.dir, '..', 'src', 'remove-ui.ts')

const azurite = await azuriteUp()

async function seed(path: string, body: string): Promise<void> {
    expect((await putBlob(path, body)).status).toBe(201)
}

function remove(removalUrl: string) {
    return spawnSync('bun', [REMOVE], { env: { ...process.env, KINOTIC_UI_REMOVAL_URL: removalUrl }, encoding: 'utf-8' })
}

describe.skipIf(!azurite)('remove-ui entrypoint (against Azurite)', () => {

    it('deletes every file of the site\'s directory and leaves other sites alone', async () => {
        const site = `removed-${crypto.randomUUID()}.apps.kinotic.test`
        const other = `kept-${crypto.randomUUID()}.apps.kinotic.test`
        await seed(`${site}/index.html`, '<html>removed</html>')
        await seed(`${site}/assets/deep/app.js`, 'console.log("removed")')
        await seed(`${other}/index.html`, '<html>kept</html>')

        const result = remove(siteUrl(site, 'dl'))

        expect(result.stderr).toBe('')
        expect(result.status).toBe(0)
        expect((await readBlob(`${site}/index.html`)).status).toBe(404)
        expect((await readBlob(`${site}/assets/deep/app.js`)).status).toBe(404)
        expect(await (await readBlob(`${other}/index.html`)).text()).toBe('<html>kept</html>')
    }, 60_000)

    it('succeeds for a site with no files', () => {
        const result = remove(siteUrl('never-published.apps.kinotic.test', 'dl'))

        expect(result.stderr).toBe('')
        expect(result.status).toBe(0)
    }, 60_000)

    it('deletes nothing through a URL that has expired', async () => {
        const site = `expired-${crypto.randomUUID()}.apps.kinotic.test`
        await seed(`${site}/index.html`, '<html>kept</html>')

        const result = remove(siteUrl(site, 'dl', new Date(Date.now() - 60_000)))

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('403')
        expect(await (await readBlob(`${site}/index.html`)).text()).toBe('<html>kept</html>')
    }, 60_000)
})
