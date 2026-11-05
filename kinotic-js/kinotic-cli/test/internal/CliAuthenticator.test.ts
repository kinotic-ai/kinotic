import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {CliAuthenticator} from '../../src/internal/CliAuthenticator.js'

describe('CliAuthenticator', () => {

    let configDir: string
    let lines: string[]

    beforeEach(() => {
        configDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-cli-auth-'))
        lines = []
    })

    afterEach(() => {
        fs.rmSync(configDir, {recursive: true, force: true})
    })

    function authenticator(server: string): CliAuthenticator {
        return new CliAuthenticator(server, configDir, {log: (message?: string) => lines.push(message ?? '')})
    }

    it('refuses to log in to a remote server over plain http', async () => {
        expect(await authenticator('http://kinotic.example').login()).to.equal(false)
        expect(lines.join('\n')).to.match(/https:\/\//)
    })

    it('refuses to connect to a remote server over plain http', async () => {
        expect(await authenticator('http://kinotic.example:58503').connect()).to.equal(false)
        expect(lines.join('\n')).to.match(/https:\/\//)
    })

    it('rejects a url that is neither http nor https', async () => {
        expect(await authenticator('ftp://kinotic.example').login()).to.equal(false)
        expect(lines.join('\n')).to.match(/only http and https/)
    })
})
