import { describe, expect, it } from 'bun:test'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { terminateStaleAlloy } from '@/internal/api/logging/AlloyManager'

function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0)
        return true
    } catch {
        return false
    }
}

describe('terminateStaleAlloy', () => {

    it('terminates the recorded process and removes the pid file', async () => {
        const orphan = spawn('sleep', ['60'])
        const pidFile = join(mkdtempSync(join(tmpdir(), 'alloy-pid-')), 'alloy.pid')
        writeFileSync(pidFile, String(orphan.pid))

        await terminateStaleAlloy(pidFile)

        expect(isAlive(orphan.pid!)).toBeFalse()
        expect(existsSync(pidFile)).toBeFalse()
    })

    it('is a no-op without a pid file', async () => {
        await terminateStaleAlloy(join(tmpdir(), 'nonexistent-dir', 'alloy.pid'))
    })

    it('tolerates a recorded process that already exited', async () => {
        const exited = spawn('true')
        await new Promise(resolve => exited.once('exit', resolve))
        const pidFile = join(mkdtempSync(join(tmpdir(), 'alloy-pid-')), 'alloy.pid')
        writeFileSync(pidFile, String(exited.pid))

        await terminateStaleAlloy(pidFile)

        expect(existsSync(pidFile)).toBeFalse()
    })
})
