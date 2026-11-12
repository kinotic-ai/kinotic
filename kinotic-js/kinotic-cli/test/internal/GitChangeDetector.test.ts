import {expect} from 'chai'
import {execFileSync} from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {GitChangeDetector} from '../../src/internal/GitChangeDetector.js'
import {ConsoleLogger} from '../../src/internal/Logger.js'

describe('GitChangeDetector', function () {

    let tempDir: string
    let repo: string
    let originalCwd: string
    let originalEnv: NodeJS.ProcessEnv

    beforeEach(function () {
        tempDir = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-git-change-')))
        originalCwd = process.cwd()
        originalEnv = {...process.env}

        // ~/.gitconfig ignores New.ts; the config GIT_CONFIG_GLOBAL points to does not
        const home = path.join(tempDir, 'home')
        fs.mkdirSync(home)
        fs.writeFileSync(path.join(home, 'ignore'), 'New.ts\n')
        fs.writeFileSync(path.join(home, '.gitconfig'), `[core]\n\texcludesFile = ${path.join(home, 'ignore')}\n`)
        fs.writeFileSync(path.join(home, 'override.gitconfig'), '[user]\n\tname = Test\n\temail = test@example.com\n')
        process.env.HOME = home
        process.env.GIT_CONFIG_NOSYSTEM = '1'
        delete process.env.XDG_CONFIG_HOME
        delete process.env.GIT_CONFIG_GLOBAL

        repo = path.join(tempDir, 'repo')
        fs.mkdirSync(path.join(repo, 'src', 'model'), {recursive: true})
        const gitEnv = {...process.env, GIT_CONFIG_GLOBAL: path.join(home, 'override.gitconfig')}
        const git = (...args: string[]) => execFileSync('git', args, {cwd: repo, env: gitEnv}).toString().trim()
        git('init', '-q')
        fs.writeFileSync(path.join(repo, 'src', 'model', 'Existing.ts'), 'export class Existing {}\n')
        fs.writeFileSync(path.join(repo, '.gitignore'), '.kinotic/\n')
        git('add', '.')
        git('commit', '-q', '-m', 'initial')
        fs.mkdirSync(path.join(repo, '.kinotic'))
        fs.writeFileSync(path.join(repo, '.kinotic', 'last-generation'), git('rev-parse', 'HEAD'))
        fs.writeFileSync(path.join(repo, 'src', 'model', 'Existing.ts'), 'export class Existing { changed = true }\n')
        process.chdir(repo)
    })

    afterEach(function () {
        process.chdir(originalCwd)
        process.env = originalEnv
        fs.rmSync(tempDir, {recursive: true, force: true})
    })

    it('performs a full scan when GIT_CONFIG_GLOBAL is set', async function () {
        process.env.GIT_CONFIG_GLOBAL = path.join(tempDir, 'home', 'override.gitconfig')
        fs.writeFileSync(path.join(repo, 'src', 'model', 'New.ts'), 'export class New {}\n')

        const changed = await new GitChangeDetector(new ConsoleLogger()).getChangedEntityFiles(['src/model'])

        expect(changed).to.equal(null)
    })

    it('uses git to find changed entity files when GIT_CONFIG_GLOBAL is not set', async function () {
        const changed = await new GitChangeDetector(new ConsoleLogger()).getChangedEntityFiles(['src/model'])

        expect(changed).to.not.equal(null)
        expect([...changed!]).to.deep.equal([path.join(repo, 'src', 'model', 'Existing.ts')])
    })
})
