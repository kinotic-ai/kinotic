import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {fileSystemSpawnEngine} from '../../src/internal/spawn/FileSystemSpawnEngine.js'

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

})
