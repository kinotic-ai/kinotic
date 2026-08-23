import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const SYNC = join(import.meta.dir, '..', 'src', 'sync.ts')

/** Runs the sync entrypoint the way its workload does: as a process with an environment. */
function runSync(env: Record<string, string>): ReturnType<typeof spawnSync> {
    return spawnSync('bun', [SYNC], { env: { ...process.env, ...env }, encoding: 'utf-8' })
}

function git(cwd: string, ...args: string[]): string {
    const result = spawnSync('git', args, { cwd, encoding: 'utf-8' })
    if (result.status !== 0) {
        throw new Error(`git ${args.join(' ')} failed: ${result.stderr}`)
    }
    return result.stdout.trim()
}

describe('sync entrypoint', () => {

    let baseDir: string
    let originDir: string
    let seedDir: string
    let workspaceDir: string

    beforeEach(() => {
        baseDir = join(tmpdir(), `workload-runner-sync-${crypto.randomUUID()}`)
        originDir = join(baseDir, 'origin.git')
        seedDir = join(baseDir, 'seed')
        workspaceDir = join(baseDir, 'workspace')

        mkdirSync(originDir, { recursive: true })
        git(originDir, 'init', '--bare', '--initial-branch=main')
        // The file transport does not advertise arbitrary shas by default the way GitHub does
        git(originDir, 'config', 'uploadpack.allowAnySHA1InWant', 'true')

        mkdirSync(seedDir, { recursive: true })
        git(seedDir, 'init', '--initial-branch=main')
        git(seedDir, 'config', 'user.email', 'fixture@kinotic.test')
        git(seedDir, 'config', 'user.name', 'Fixture')
        writeFileSync(join(seedDir, 'package.json'), JSON.stringify({ name: 'fixture', version: '1.0.0' }))
        writeFileSync(join(seedDir, 'main.ts'), 'export const version = 1\n')
        git(seedDir, 'add', '.')
        git(seedDir, 'commit', '-m', 'initial')
        git(seedDir, 'push', `file://${originDir}`, 'main')
    })

    afterEach(() => {
        rmSync(baseDir, { recursive: true, force: true })
    })

    function commitChange(): string {
        writeFileSync(join(seedDir, 'main.ts'), 'export const version = 2\n')
        git(seedDir, 'add', '.')
        git(seedDir, 'commit', '-m', 'update')
        git(seedDir, 'push', `file://${originDir}`, 'main')
        return git(seedDir, 'rev-parse', 'HEAD')
    }

    it('checks out the requested commit and writes the sentinel last', () => {
        const sha = git(seedDir, 'rev-parse', 'HEAD')

        const result = runSync({
            GIT_CLONE_URL: `file://${originDir}`,
            GIT_REF: sha,
            KINOTIC_WORKSPACE_DIR: workspaceDir,
        })

        expect(result.status).toBe(0)
        expect(readFileSync(join(workspaceDir, 'main.ts'), 'utf-8')).toContain('version = 1')
        expect(readFileSync(join(workspaceDir, '.kinotic', 'reload'), 'utf-8')).toBe(sha)
    }, 30_000)

    it('updates an existing checkout to a new commit', () => {
        const first = git(seedDir, 'rev-parse', 'HEAD')
        runSync({ GIT_CLONE_URL: `file://${originDir}`, GIT_REF: first, KINOTIC_WORKSPACE_DIR: workspaceDir })
        const second = commitChange()

        const result = runSync({
            GIT_CLONE_URL: `file://${originDir}`,
            GIT_REF: second,
            KINOTIC_WORKSPACE_DIR: workspaceDir,
        })

        expect(result.status).toBe(0)
        expect(readFileSync(join(workspaceDir, 'main.ts'), 'utf-8')).toContain('version = 2')
        expect(readFileSync(join(workspaceDir, '.kinotic', 'reload'), 'utf-8')).toBe(second)
    }, 30_000)

    it('fails without touching the sentinel when the ref cannot be fetched', () => {
        const result = runSync({
            GIT_CLONE_URL: `file://${originDir}`,
            GIT_REF: '0123456789012345678901234567890123456789',
            KINOTIC_WORKSPACE_DIR: workspaceDir,
        })

        expect(result.status).not.toBe(0)
        expect(existsSync(join(workspaceDir, '.kinotic', 'reload'))).toBe(false)
    }, 30_000)

    it('never persists the token into the shared checkout', () => {
        const sha = git(seedDir, 'rev-parse', 'HEAD')

        const result = runSync({
            GIT_CLONE_URL: `file://${originDir}`,
            GIT_REF: sha,
            GIT_TOKEN: 'ghs_fixture_token',
            KINOTIC_WORKSPACE_DIR: workspaceDir,
        })

        expect(result.status).toBe(0)
        const gitConfig = readFileSync(join(workspaceDir, '.git', 'config'), 'utf-8')
        expect(gitConfig).not.toContain('ghs_fixture_token')
        expect(gitConfig).not.toContain('extraheader')
    }, 30_000)
})
