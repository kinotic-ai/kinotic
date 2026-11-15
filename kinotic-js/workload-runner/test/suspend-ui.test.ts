import { describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { azuriteUp, putBlob, readBlob, siteUrl } from './azurite.ts'

const SUSPEND = join(import.meta.dir, '..', 'src', 'suspend-ui.ts')
const RESUME = join(import.meta.dir, '..', 'src', 'resume-ui.ts')
const DEACTIVATED_URL = 'https://portal.kinotic.test/deactivated/acme'
const COMMIT = 'c'.repeat(40)

const azurite = await azuriteUp()

async function publish(site: string): Promise<void> {
    expect((await putBlob(`${site}/index.html`, '<html>published</html>', {
        'Content-Type': 'text/html',
        'x-ms-blob-cache-control': 'no-cache',
        'x-ms-meta-commit': COMMIT,
    })).status).toBe(201)
}

function suspend(site: string) {
    return spawnSync('bun', [SUSPEND], {
        env: { ...process.env, KINOTIC_UI_INDEX_URL: siteUrl(site, 'rcwdl'), KINOTIC_UI_DEACTIVATED_URL: DEACTIVATED_URL },
        encoding: 'utf-8',
    })
}

function resume(site: string) {
    return spawnSync('bun', [RESUME], { env: { ...process.env, KINOTIC_UI_INDEX_URL: siteUrl(site, 'rcwdl') }, encoding: 'utf-8' })
}

describe.skipIf(!azurite)('suspend-ui and resume-ui entrypoints (against Azurite)', () => {

    it('serves a suspended site a redirect to the deactivated page, and its own index again once resumed', async () => {
        const site = `suspended-${crypto.randomUUID()}.apps.kinotic.test`
        await publish(site)

        const suspended = suspend(site)
        expect(suspended.stderr).toBe('')
        expect(suspended.status).toBe(0)
        const page = await readBlob(`${site}/index.html`)
        expect(await page.text()).toContain(`<meta http-equiv="refresh" content="0; url=${DEACTIVATED_URL}">`)
        expect(page.headers.get('cache-control')).toBe('no-cache')
        expect(page.headers.get('x-ms-meta-commit')).toBe(COMMIT)

        const resumed = resume(site)
        expect(resumed.stderr).toBe('')
        expect(resumed.status).toBe(0)
        const index = await readBlob(`${site}/index.html`)
        expect(await index.text()).toBe('<html>published</html>')
        expect(index.headers.get('content-type')).toContain('text/html')
        expect(index.headers.get('x-ms-meta-commit')).toBe(COMMIT)
        expect(index.headers.get('x-ms-meta-suspended')).toBeNull()
        expect((await readBlob(`${site}/.kinotic/index.html`)).status).toBe(404)
    }, 60_000)

    it('keeps the published index when a suspended site is suspended again', async () => {
        const site = `twice-${crypto.randomUUID()}.apps.kinotic.test`
        await publish(site)

        expect(suspend(site).status).toBe(0)
        expect(suspend(site).status).toBe(0)
        expect(resume(site).status).toBe(0)

        expect(await (await readBlob(`${site}/index.html`)).text()).toBe('<html>published</html>')
    }, 60_000)

    it('leaves a site that is not suspended, or was never published, as it is', async () => {
        const site = `served-${crypto.randomUUID()}.apps.kinotic.test`
        await publish(site)

        expect(resume(site).status).toBe(0)
        expect(await (await readBlob(`${site}/index.html`)).text()).toBe('<html>published</html>')

        const unpublished = `unpublished-${crypto.randomUUID()}.apps.kinotic.test`
        expect(suspend(unpublished).status).toBe(0)
        expect(resume(unpublished).status).toBe(0)
        expect((await readBlob(`${unpublished}/index.html`)).status).toBe(404)
    }, 60_000)

    it('leaves the index a publish wrote while suspended, and drops the index kept before it', async () => {
        const site = `republished-${crypto.randomUUID()}.apps.kinotic.test`
        await publish(site)
        expect(suspend(site).status).toBe(0)
        expect((await putBlob(`${site}/index.html`, '<html>republished</html>', { 'x-ms-meta-commit': 'e'.repeat(40) })).status).toBe(201)

        const result = resume(site)

        expect(result.status).toBe(0)
        expect(await (await readBlob(`${site}/index.html`)).text()).toBe('<html>republished</html>')
        expect((await readBlob(`${site}/.kinotic/index.html`)).status).toBe(404)
    }, 60_000)

    it('fails to resume a suspended site whose kept index is gone', async () => {
        const site = `lost-${crypto.randomUUID()}.apps.kinotic.test`
        expect((await putBlob(`${site}/index.html`, '<html>deactivated</html>', { 'x-ms-meta-suspended': 'true' })).status).toBe(201)

        const result = resume(site)

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('publish the project again')
    }, 60_000)
})
