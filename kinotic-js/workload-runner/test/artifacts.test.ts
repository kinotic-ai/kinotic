import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'
import { findArtifacts } from '../src/artifacts.ts'

describe('findArtifacts', () => {

    let workspaceDir: string

    beforeEach(() => {
        workspaceDir = join(tmpdir(), `workload-runner-artifacts-${crypto.randomUUID()}`)
        mkdirSync(workspaceDir, { recursive: true })
    })

    afterEach(() => {
        rmSync(workspaceDir, { recursive: true, force: true })
    })

    function write(files: Record<string, string>): void {
        for (const [path, content] of Object.entries(files)) {
            const file = join(workspaceDir, path)
            mkdirSync(dirname(file), { recursive: true })
            writeFileSync(file, content)
        }
    }

    it('finds microservices and UIs by where they sit in the checkout', () => {
        write({
            'package.json': '{"name": "fixture", "workspaces": ["packages/*"]}',
            'packages/domain/package.json': '{"name": "@acme/domain"}',
            'packages/microservices/orders/package.json': '{"name": "@acme/orders"}',
            'packages/microservices/billing-svc/package.json': '{"name": "billing", "main": "./dist/index.js"}',
            // nested package.json files never count as artifacts
            'packages/microservices/orders/node_modules/thing/package.json': '{"name": "thing"}',
            'packages/ui/admin/package.json': '{"name": "@acme/admin", "scripts": {"build": "vite build"}}',
            // a package under packages/ui without a build script is a library, not a UI
            'packages/ui/shared/package.json': '{"name": "@acme/shared"}',
            // a file directly under the kind directory is not a package
            'packages/ui/README.md': '# uis',
        })

        const artifacts = findArtifacts(workspaceDir)

        // ordered by name, identified by the unscoped package name, never the directory
        expect(artifacts.microservices).toEqual([
            { name: 'billing', dir: 'packages/microservices/billing-svc', entry: 'dist/index.js' },
            { name: 'orders', dir: 'packages/microservices/orders', entry: 'src/main.ts' },
        ])
        expect(artifacts.uis).toEqual([{ name: 'admin', dir: 'packages/ui/admin' }])
    })

    it('finds nothing in a checkout without package directories', () => {
        write({ 'package.json': '{"name": "fixture"}' })

        expect(findArtifacts(workspaceDir)).toEqual({ microservices: [], uis: [] })
    })

    it('fails naming a package whose name is not a label', () => {
        write({ 'packages/ui/admin/package.json': '{"name": "@acme/Admin_UI", "scripts": {"build": "vite build"}}' })

        expect(() => findArtifacts(workspaceDir)).toThrow(/packages\/ui\/admin.*@acme\/Admin_UI/)
    })

    it('fails naming a package without a name', () => {
        write({ 'packages/microservices/orders/package.json': '{"version": "1.0.0"}' })

        expect(() => findArtifacts(workspaceDir)).toThrow(/packages\/microservices\/orders/)
    })

    it('fails when two packages of one kind share a name', () => {
        write({
            'packages/microservices/orders/package.json': '{"name": "@acme/orders"}',
            'packages/microservices/orders-v2/package.json': '{"name": "@legacy/orders"}',
        })

        expect(() => findArtifacts(workspaceDir))
            .toThrow(/packages\/microservices\/orders.*packages\/microservices\/orders-v2.*'orders'/)
    })
})
