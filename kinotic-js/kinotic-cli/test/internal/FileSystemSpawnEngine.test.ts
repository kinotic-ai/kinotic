import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {FileSystemSpawnEngine, fileSystemSpawnEngine} from '../../src/internal/spawn/FileSystemSpawnEngine.js'

function spawnDirWith(files: Record<string, string>): string {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-src-'))
    for (const [rel, content] of Object.entries(files)) {
        const p = path.join(dir, rel)
        fs.mkdirSync(path.dirname(p), {recursive: true})
        fs.writeFileSync(p, content)
    }
    return dir
}

function engineFor(spawnDir: string): FileSystemSpawnEngine {
    return new FileSystemSpawnEngine({resolveSpawn: async () => spawnDir})
}

describe('FileSystemSpawnEngine', () => {

    it('renders the bundled project spawn to disk', async () => {
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'acme')

        const context = await fileSystemSpawnEngine.renderSpawn('project', destination,
            {projectName: 'acme', organization: 'acme-org', application: 'acme-app'})

        const rootPackage = JSON.parse(fs.readFileSync(path.join(destination, 'package.json'), 'utf8'))
        expect(rootPackage.name).to.equal('acme')
        expect(rootPackage.catalog['@kinotic-ai/core']).to.equal(context.kinoticApiVersion)

        const domainPackage = JSON.parse(fs.readFileSync(path.join(destination, 'packages/domain/package.json'), 'utf8'))
        expect(domainPackage.name).to.equal('acme/domain')

        const projectConfig = fs.readFileSync(path.join(destination, '.config/kinotic.config.ts'), 'utf8')
        expect(projectConfig).to.contain('organization: "acme-org"')
        expect(projectConfig).to.contain('application: "acme-app"')

        expect(fs.existsSync(path.join(destination, 'packages/domain/model/.gitkeep'))).to.be.true
        expect(fs.existsSync(path.join(destination, '.gitignore'))).to.be.true
        expect(fs.existsSync(path.join(destination, 'spawn.json'))).to.be.false
        expect(fs.existsSync(path.join(destination, 'package.json.liquid'))).to.be.false
    })

    it('renders the bundled library spawn to disk', async () => {
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'my-lib')

        await fileSystemSpawnEngine.renderSpawn('library', destination, {libraryName: 'my-lib'})

        const libraryPackage = JSON.parse(fs.readFileSync(path.join(destination, 'package.json'), 'utf8'))
        expect(libraryPackage.name).to.equal('my-lib')
        expect(fs.readFileSync(path.join(destination, 'src/index.ts'), 'utf8')).to.not.be.empty
    })

    it('fails when the destination already exists', async () => {
        const destination = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-'))

        try {
            await fileSystemSpawnEngine.renderSpawn('project', destination, {projectName: 'acme'})
            expect.fail('renderSpawn should have thrown')
        } catch (err) {
            expect((err as Error).message).to.contain('already exists')
        }
    })

    it('refuses to write outside the destination when a path escapes the root', async () => {
        // A property value injects ../ into a templated path; the write must be refused.
        const spawnDir = spawnDirWith({'{{ out }}.txt': 'pwned'})
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'app')

        try {
            await engineFor(spawnDir).renderSpawn('evil', destination, {out: '../../../escape'})
            expect.fail('renderSpawn should have thrown')
        } catch (err) {
            expect((err as Error).message).to.contain('escapes')
            expect(fs.existsSync(path.resolve(destination, '../../../escape.txt'))).to.be.false
        }
    })

    it('refuses an inheritance ref that escapes the root', async () => {
        const spawnDir = spawnDirWith({'spawn.json': JSON.stringify({inherits: '../../../../etc'})})
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'app')

        try {
            await engineFor(spawnDir).renderSpawn('evil', destination, {})
            expect.fail('renderSpawn should have thrown')
        } catch (err) {
            expect((err as Error).message).to.contain('escapes')
        }
    })

})
