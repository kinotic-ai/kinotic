import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import type { ProjectDependencies } from '@kinotic-ai/management-api'
import { dependencyHashOf, readDependencies } from '../src/sbom.ts'

/** Lockfiles `bun install` wrote, each for a package.json exercising one part of the tree. */
const FIXTURES = join(import.meta.dir, 'fixtures')

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

    "lodash": ["lodash@4.17.21", "", {}, "sha512-v2kDEe57lecTulaDIuNTPy3Ry4gLGJ6Z1O3vE1krgXZNrsQ+LFTGHVxVjcXPs17LhbZVGedAJv8XZ1tvj5FvSg=="],
  }
}
`

/** The package URLs at the given positions of the tree. */
function purls(tree: ProjectDependencies, positions: number[]): string[] {
    return positions.map(position => tree.packages[position]!)
}

/** Every edge of the tree as `dependent -> dependency` package URLs. */
function edges(tree: ProjectDependencies): string[] {
    return tree.edges.map(([dependent, dependency]) => `${tree.packages[dependent]} -> ${tree.packages[dependency]}`)
}

describe('project SBOM', () => {

    let workspaceDir: string

    beforeEach(() => {
        workspaceDir = join(tmpdir(), `workload-runner-sbom-${crypto.randomUUID()}`)
        mkdirSync(workspaceDir, { recursive: true })
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    function locked(fixture: string): ProjectDependencies {
        copyFileSync(join(FIXTURES, fixture), join(workspaceDir, 'bun.lock'))
        return readDependencies(workspaceDir)
    }

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

            writeFileSync(join(workspaceDir, 'main.ts'), 'export const changed = true\n')

            expect(dependencyHashOf(workspaceDir)).toBe(hash)
        })
    })

    describe('dependency tree', () => {

        it('lists every package bun.lock installs once, and none of the project\'s own workspace packages', () => {
            writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE)

            const tree = readDependencies(workspaceDir)

            expect(tree.packages).toEqual(['pkg:npm/lodash@4.17.21'])
            expect(tree.direct).toEqual([0])
        })

        it('lists a package installed under several keys, such as an alias, once', () => {
            const tree = locked('nesting.bun.lock')

            // string-width-cjs is an alias of string-width@4.2.3, which is also installed under its own name
            expect(tree.packages.filter(purl => purl === 'pkg:npm/string-width@4.2.3')).toHaveLength(1)
            expect(tree.packages).toHaveLength(17)
        })

        it('encodes a scoped name\'s @ in the package URL', () => {
            const tree = locked('nesting.bun.lock')

            expect(tree.packages).toContain('pkg:npm/%40isaacs/cliui@8.0.2')
        })

        it('resolves a dependency from the nearest key up the dependent\'s path, as bun loads it', () => {
            const tree = locked('nesting.bun.lock')

            // @isaacs/cliui/wrap-ansi loads the strip-ansi and string-width bun installed under
            // @isaacs/cliui, one level up, not the older ones at the top level
            expect(edges(tree)).toContain('pkg:npm/wrap-ansi@8.1.0 -> pkg:npm/strip-ansi@7.2.0')
            expect(edges(tree)).toContain('pkg:npm/wrap-ansi@8.1.0 -> pkg:npm/string-width@5.1.2')
            expect(edges(tree)).not.toContain('pkg:npm/wrap-ansi@8.1.0 -> pkg:npm/strip-ansi@6.0.1')
            expect(edges(tree)).not.toContain('pkg:npm/wrap-ansi@8.1.0 -> pkg:npm/string-width@4.2.3')
            // an alias is loaded under its own name: string-width-cjs is string-width@4.2.3
            expect(edges(tree)).toContain('pkg:npm/%40isaacs/cliui@8.0.2 -> pkg:npm/string-width@4.2.3')
        })

        it('resolves a workspace member\'s dependencies from the versions bun installs under its name', () => {
            const tree = locked('workspace-member.bun.lock')

            expect(tree.packages).not.toContain('pkg:npm/pkg-a@workspace%3Apackages%2Fpkg-a')
            expect(purls(tree, tree.direct)).toEqual(['pkg:npm/strip-ansi@6.0.1', 'pkg:npm/strip-ansi@7.1.0'])
            // pkg-a depends on strip-ansi 7.1.0 in production, so nothing is development
            expect(tree.development).toEqual([])
            expect(edges(tree)).toContain('pkg:npm/strip-ansi@7.1.0 -> pkg:npm/ansi-regex@6.4.0')
        })

        it('marks what the project\'s package.json files declare as direct, whatever kind of dependency', () => {
            const tree = locked('scopes.bun.lock')

            expect(purls(tree, tree.direct)).toEqual(['pkg:npm/%40bomb.sh/tab@0.0.19', 'pkg:npm/cac@6.7.14', 'pkg:npm/is-even@1.0.0',
                                                      'pkg:npm/is-number@7.0.0', 'pkg:npm/is-odd@3.0.1'])
        })

        it('scopes packages by how production dependencies reach them', () => {
            const tree = locked('scopes.bun.lock')

            // nothing but devDependencies reach is-even and what it loads
            expect(purls(tree, tree.development)).toEqual(['pkg:npm/is-buffer@1.1.6', 'pkg:npm/is-even@1.0.0', 'pkg:npm/is-number@3.0.0',
                                                           'pkg:npm/is-odd@0.1.2', 'pkg:npm/kind-of@3.2.2'])
            // is-number@7.0.0 is an optionalDependency, and a devDependency installs cac, which
            // production reaches only as an optional peer of @bomb.sh/tab
            expect(purls(tree, tree.optional)).toEqual(['pkg:npm/cac@6.7.14', 'pkg:npm/is-number@7.0.0'])
            expect(edges(tree)).toContain('pkg:npm/%40bomb.sh/tab@0.0.19 -> pkg:npm/cac@6.7.14')
            // is-odd@3.0.1 loads the is-number@6.0.0 installed under it
            expect(edges(tree)).toContain('pkg:npm/is-odd@3.0.1 -> pkg:npm/is-number@6.0.0')
        })

        it('leaves out a peer nothing installs', () => {
            const tree = locked('scopes.bun.lock')

            // @bomb.sh/tab's optional peers citty and commander are not installed
            expect(edges(tree).filter(edge => edge.startsWith('pkg:npm/%40bomb.sh/tab@0.0.19 -> '))).toEqual(['pkg:npm/%40bomb.sh/tab@0.0.19 -> pkg:npm/cac@6.7.14'])
        })

        it('versions a package bun installed from outside a registry by its source', () => {
            const tree = locked('tarball.bun.lock')

            const tarball = 'pkg:npm/is-odd@' + encodeURIComponent('https://registry.npmjs.org/is-odd/-/is-odd-3.0.1.tgz')
            expect(tree.packages).toContain(tarball)
            expect(edges(tree)).toEqual([`${tarball} -> pkg:npm/is-number@6.0.0`])
        })

        it('reads the lockfile version 2 bun 1.4 writes as it reads version 1', () => {
            const tree = locked('scopes.bun.lock')
            writeFileSync(join(workspaceDir, 'bun.lock'), readFileSync(join(FIXTURES, 'scopes-v2.bun.lock')))

            expect(readDependencies(workspaceDir)).toEqual(tree)
        })

        it('refuses a lockfile version it does not read', () => {
            writeFileSync(join(workspaceDir, 'bun.lock'), LOCKFILE.replace('"lockfileVersion": 1', '"lockfileVersion": 3'))

            expect(() => readDependencies(workspaceDir)).toThrow('bun.lock is lockfile version 3')
        })
    })
})
