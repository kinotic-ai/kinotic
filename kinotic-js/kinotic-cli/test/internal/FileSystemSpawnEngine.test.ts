import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {FileSystemSpawnEngine, fileSystemSpawnEngine} from '../../src/internal/spawn/FileSystemSpawnEngine.js'

describe('FileSystemSpawnEngine', () => {

    it('renders the bundled project spawn to disk', async () => {
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'acme')

        await fileSystemSpawnEngine.renderSpawn('project', destination,
            {projectName: 'acme', organization: 'acme-org', application: 'acme-app'})

        const rootPackage = JSON.parse(fs.readFileSync(path.join(destination, 'package.json'), 'utf8'))
        expect(rootPackage.name).to.equal('acme')

        // the generated project pins what the CLI itself depends on, not the spawn.json globals
        const cliPackage = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '../../package.json'), 'utf8'))
        expect(rootPackage.catalog['@kinotic-ai/core']).to.equal(cliPackage.dependencies['@kinotic-ai/core'])
        expect(rootPackage.catalog['@kinotic-ai/persistence']).to.equal(cliPackage.dependencies['@kinotic-ai/persistence'])
        expect(rootPackage.devDependencies['@kinotic-ai/os-api']).to.equal(cliPackage.dependencies['@kinotic-ai/os-api'])
        expect(rootPackage.devDependencies['@kinotic-ai/kinotic-cli']).to.equal(`^${cliPackage.version}`)

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

    it('overrides a spawn global with the CLI dependency range', async () => {
        const spawnDir = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-src-'))
        fs.writeFileSync(path.join(spawnDir, 'spawn.json'),
            JSON.stringify({globals: {kinoticCoreVersion: '^0.0.1'}}))
        fs.writeFileSync(path.join(spawnDir, 'core.txt.liquid'), '{{ kinoticCoreVersion }}')
        const engine = new FileSystemSpawnEngine({resolveSpawn: async () => spawnDir})

        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'out')
        await engine.renderSpawn('stale', destination)

        const cliPackage = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '../../package.json'), 'utf8'))
        expect(fs.readFileSync(path.join(destination, 'core.txt'), 'utf8'))
            .to.equal(cliPackage.dependencies['@kinotic-ai/core']).and.not.equal('^0.0.1')
    })

    it('renders the bundled library spawn to disk', async () => {
        const destination = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-test-')), 'my-lib')

        await fileSystemSpawnEngine.renderSpawn('library', destination, {libraryName: 'my-lib'})

        const libraryPackage = JSON.parse(fs.readFileSync(path.join(destination, 'package.json'), 'utf8'))
        expect(libraryPackage.name).to.equal('my-lib')
        expect(fs.readFileSync(path.join(destination, 'src/index.ts'), 'utf8')).to.not.be.empty
    })

})
