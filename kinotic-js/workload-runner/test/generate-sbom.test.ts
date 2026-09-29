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

    // no server is configured: each run fails before connecting to one
    function environment(): Record<string, string> {
        return {
            KINOTIC_PROJECT_ID: 'shop',
            KINOTIC_WORKSPACE_DIR: workspaceDir,
        }
    }

    it('fails naming the variable when the project is not given', () => {
        const { KINOTIC_PROJECT_ID, ...rest } = environment()

        const result = runGenerateSbom(rest)

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('KINOTIC_PROJECT_ID must be set')
    })

    it('fails before reporting anything when the checkout has no bun.lock', () => {
        const result = runGenerateSbom(environment())

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('has no bun.lock to read the SBOM from')
    })

    it('fails before reporting anything when the tree is larger than one report carries', () => {
        const packages = Array.from({ length: 40_000 }, (_, i) => `package-with-a-name-long-enough-${String(i).padStart(6, '0')}`)
        writeFileSync(join(workspaceDir, 'bun.lock'), JSON.stringify({
            lockfileVersion: 1,
            workspaces: { '': { name: 'fixture' } },
            packages: Object.fromEntries(packages.map(name => [name, [`${name}@1.0.0`, '', {}, 'sha512-test']])),
        }))

        const result = runGenerateSbom(environment())

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('the SBOM of 40000 packages is')
        expect(result.stderr).toContain('one report to the server carries')
    })
})
