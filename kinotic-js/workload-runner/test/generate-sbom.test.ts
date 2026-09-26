import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const GENERATE_SBOM = join(import.meta.dir, '..', 'src', 'generate-sbom.ts')

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
            // never contacted: each run fails before anything is uploaded
            KINOTIC_SBOM_UPLOAD_URL: 'https://storage.example.test/organizations/acme/sboms/shop.cdx.json?sv=2020-12-06&sig=test',
            KINOTIC_PROJECT_ID: 'shop',
            KINOTIC_WORKSPACE_DIR: workspaceDir,
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
})
