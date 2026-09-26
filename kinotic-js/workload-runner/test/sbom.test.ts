import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { dependencyHashOf, generateSbom } from '../src/sbom.ts'

const LODASH_INTEGRITY = 'sha512-v2kDEe57lecTulaDIuNTPy3Ry4gLGJ6Z1O3vE1krgXZNrsQ+LFTGHVxVjcXPs17LhbZVGedAJv8XZ1tvj5FvSg=='

/** A Bun workspace whose microservice depends on lodash, locked the way `bun install` writes bun.lock. */
const LOCKFILE = `{
  "lockfileVersion": 1,
  "workspaces": {
    "": {
      "name": "fixture",
    },
    "packages/microservices/orders": {
      "name": "@fixture/orders",
      "version": "0.1.0",
      "dependencies": {
        "lodash": "^4.17.21",
      },
    },
  },
  "packages": {
    "@fixture/orders": ["@fixture/orders@workspace:packages/microservices/orders"],

    "lodash": ["lodash@4.17.21", "", {}, "${LODASH_INTEGRITY}"],
  }
}
`

describe('project SBOM', () => {

    let workspaceDir: string

    beforeEach(() => {
        workspaceDir = join(tmpdir(), `workload-runner-sbom-${crypto.randomUUID()}`)
        mkdirSync(join(workspaceDir, 'packages', 'microservices', 'orders'), { recursive: true })
        writeFileSync(join(workspaceDir, 'package.json'),
                      JSON.stringify({ name: 'fixture', private: true, workspaces: ['packages/microservices/*'] }))
        writeFileSync(join(workspaceDir, 'packages', 'microservices', 'orders', 'package.json'),
                      JSON.stringify({ name: '@fixture/orders', version: '0.1.0', dependencies: { lodash: '^4.17.21' } }))
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    describe('dependency hash', () => {

        it('is null for a checkout without a bun.lock', () => {
            expect(dependencyHashOf(workspaceDir)).toBeNull()
        })

        it('is the same for the same bun.lock and changes with it', () => {
            writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE)
            const hash = dependencyHashOf(workspaceDir)

            expect(hash).toMatch(/^[0-9a-f]{64}$/)
            expect(dependencyHashOf(workspaceDir)).toBe(hash)

            writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE.replaceAll('4.17.21', '4.17.20'))
            expect(dependencyHashOf(workspaceDir)).not.toBe(hash)
        })

        it('ignores what the lockfile does not record', () => {
            writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE)
            const hash = dependencyHashOf(workspaceDir)

            writeFileSync(join(workspaceDir, 'packages', 'microservices', 'orders', 'main.ts'), 'export const changed = true\n')

            expect(dependencyHashOf(workspaceDir)).toBe(hash)
        })
    })

    it('generates a CycloneDX 1.6 document of every package bun.lock resolves, with its hash and license', async () => {
        writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE)
        const output = join(workspaceDir, '..', `sbom-${crypto.randomUUID()}.cdx.json`)

        try {
            const document = await generateSbom(workspaceDir, output)

            expect(document.bomFormat).toBe('CycloneDX')
            expect(document.specVersion).toBe('1.6')
            const lodash = (document.components as Array<Record<string, any>>).find(component => component.name === 'lodash')
            expect(lodash?.version).toBe('4.17.21')
            expect(lodash?.purl).toBe('pkg:npm/lodash@4.17.21')
            expect(lodash?.hashes).toContainEqual({
                alg: 'SHA-512',
                content: Buffer.from(LODASH_INTEGRITY.slice('sha512-'.length), 'base64').toString('hex'),
            })
            // the license comes from the npm registry, which the SBOM workload may reach
            expect(JSON.stringify(lodash?.licenses)).toContain('MIT')
        } finally {
            rmSync(output, { force: true })
        }
    }, 120_000)
})
