import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const LOG = join(import.meta.dir, '..', 'src', 'log.ts')

/**
 * Runs a script over the log module the way an entrypoint does: as a process whose
 * environment carries the node's log contract.
 */
function runWithSink(script: string, env: Record<string, string>): ReturnType<typeof spawnSync> {
    return spawnSync('bun', ['-e', `import { log } from ${JSON.stringify(LOG)}\n${script}`],
                     { env: { ...process.env, ...env }, encoding: 'utf-8', maxBuffer: 16 * 1024 * 1024 })
}

describe('log sink', () => {

    let logDir: string

    beforeEach(() => {
        logDir = join(tmpdir(), `workload-runner-log-${crypto.randomUUID()}`)
        mkdirSync(logDir, { recursive: true })
    })

    afterEach(() => {
        rmSync(logDir, { recursive: true, force: true })
    })

    it('writes nothing but stdout when the node names no log directory', () => {
        const result = runWithSink(`log('hello')`, { KINOTIC_LOG_DIR: '' })

        expect(result.stdout).toBe('hello\n')
        expect(readdirSync(logDir)).toEqual([])
    })

    it('mirrors output into workload.log under the named directory', () => {
        const result = runWithSink(`log('hello')`, {
            KINOTIC_LOG_DIR: logDir, KINOTIC_LOG_MAX_SIZE_MB: '1', KINOTIC_LOG_MAX_FILES: '2',
        })

        expect(result.stdout).toBe('hello\n')
        expect(readFileSync(join(logDir, 'workload.log'), 'utf-8')).toBe('hello\n')
    })

    it('rotates at the size limit and keeps only the configured rotated files', () => {
        // 1 MB cap, 600 KB lines: every line after the first rotates, and two rotations survive
        const result = runWithSink(`for (let i = 0; i < 5; i++) log(String(i).repeat(600 * 1024))`, {
            KINOTIC_LOG_DIR: logDir, KINOTIC_LOG_MAX_SIZE_MB: '1', KINOTIC_LOG_MAX_FILES: '2',
        })

        expect(result.status).toBe(0)
        expect(readdirSync(logDir).sort()).toEqual(['workload.log', 'workload.log.1', 'workload.log.2'])
        expect(readFileSync(join(logDir, 'workload.log'), 'utf-8')[0]).toBe('4')
        expect(readFileSync(join(logDir, 'workload.log.1'), 'utf-8')[0]).toBe('3')
        expect(readFileSync(join(logDir, 'workload.log.2'), 'utf-8')[0]).toBe('2')
        for (const file of readdirSync(logDir)) {
            expect(statSync(join(logDir, file)).size).toBeLessThanOrEqual(1024 * 1024)
        }
        expect(existsSync(join(logDir, 'workload.log.3'))).toBeFalse()
    })

    it('refuses a log directory without a rotation policy', () => {
        const result = runWithSink(`log('hello')`, { KINOTIC_LOG_DIR: logDir })

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('KINOTIC_LOG_MAX_SIZE_MB')
    })
})
