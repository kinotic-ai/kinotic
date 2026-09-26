import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { azuriteUp, containerSas, createContainer, ENDPOINT, readBlob, writeBlob } from './azurite.ts'

const GENERATE_SBOM = join(import.meta.dir, '..', 'src', 'generate-sbom.ts')

const CONTAINER = 'organizations'
const DIRECTORY = 'acme/sboms/shop'
const COMMIT = 'a'.repeat(40)

const azurite = await azuriteUp()

/** Runs the SBOM entrypoint the way its workload does: as a process with an environment. */
function runGenerateSbom(env: Record<string, string>): ReturnType<typeof spawnSync> {
    return spawnSync('bun', [GENERATE_SBOM], { env: { ...process.env, ...env }, encoding: 'utf-8' })
}

describe('generate-sbom entrypoint', () => {

    let workspaceDir: string

    beforeEach(() => {
        workspaceDir = join(tmpdir(), `workload-runner-generate-sbom-${crypto.randomUUID()}`)
        mkdirSync(workspaceDir, { recursive: true })
        writeFileSync(join(workspaceDir, 'package.json'), JSON.stringify({ name: 'fixture', private: true }))
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    function environment(): Record<string, string> {
        return {
            KINOTIC_SBOM_UPLOAD_URL: `${ENDPOINT}/${CONTAINER}/${DIRECTORY}?${containerSas(CONTAINER)}`,
            KINOTIC_SBOM_FILE: `${COMMIT}.cdx.json`,
            KINOTIC_SBOM_COMMIT: COMMIT,
            KINOTIC_PROJECT_ID: 'shop',
            KINOTIC_WORKSPACE_DIR: workspaceDir,
            // nothing listens here, so recording the SBOM fails
            KINOTIC_SERVER_HOST: '127.0.0.1',
            KINOTIC_SERVER_PORT: '58504',
            KINOTIC_CLIENT_ID: 'machine-1',
            KINOTIC_CLIENT_SECRET: 'machine-secret',
        }
    }

    it('fails naming the variable when the upload URL is not given', () => {
        const { KINOTIC_SBOM_UPLOAD_URL, ...rest } = environment()

        const result = runGenerateSbom(rest)

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('KINOTIC_SBOM_UPLOAD_URL must be set')
    })

    it('fails before uploading anything when the checkout has no bun.lock', () => {
        const result = runGenerateSbom(environment())

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('has no bun.lock to generate the SBOM from')
    })

    describe.skipIf(!azurite)('against Azurite', () => {

        beforeEach(async () => {
            await createContainer(CONTAINER)
            writeFileSync(join(workspaceDir, 'bun.lock'), JSON.stringify({ lockfileVersion: 1, workspaces: { '': { name: 'fixture' } }, packages: {} }))
        })

        it('uploads the SBOM stamped with its commit, and leaves the files of other commits when the record fails', async () => {
            await writeBlob(CONTAINER, `${DIRECTORY}/${'b'.repeat(40)}.cdx.json`, '{"bomFormat": "CycloneDX"}', 'b'.repeat(40))

            const result = runGenerateSbom(environment())

            expect(result.status).not.toBe(0)
            expect(result.stderr).toContain('[workload-runner] SBOM generation failed')
            const uploaded = await readBlob(CONTAINER, `${DIRECTORY}/${COMMIT}.cdx.json`)
            expect(uploaded.status).toBe(200)
            expect(uploaded.headers.get('content-type')).toBe('application/vnd.cyclonedx+json')
            expect(uploaded.headers.get('x-ms-meta-commit')).toBe(COMMIT)
            expect((await uploaded.json()).bomFormat).toBe('CycloneDX')
            // the server still reads the SBOM it knows of, so that file stays
            expect((await readBlob(CONTAINER, `${DIRECTORY}/${'b'.repeat(40)}.cdx.json`)).status).toBe(200)
        }, 120_000)
    })
})
