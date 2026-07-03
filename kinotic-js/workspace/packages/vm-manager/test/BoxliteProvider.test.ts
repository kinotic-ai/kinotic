import { describe, expect, it } from 'bun:test'
import { Workload } from '@kinotic-ai/os-api'
import {
    CONSOLE_LOG_FILE,
    GUEST_LOG_DIR,
    wrapEntrypointForLogCapture,
} from '@/internal/api/providers/BoxliteProvider'

describe('wrapEntrypointForLogCapture', () => {

    it('wraps an explicit entrypoint so stdout/stderr append to the console log', () => {
        const workload = new Workload('build', 'alpine:latest')
        workload.entrypoint = ['/bin/run-build', '--verbose']

        const wrapped = wrapEntrypointForLogCapture(workload)

        expect(wrapped).toEqual([
            '/bin/sh', '-c',
            `exec "$@" >> ${GUEST_LOG_DIR}/${CONSOLE_LOG_FILE} 2>&1`,
            'sh',
            '/bin/run-build', '--verbose',
        ])
    })

    it('appends cmd after the entrypoint so the full command is captured', () => {
        const workload = new Workload('build', 'alpine:latest')
        workload.entrypoint = ['/bin/sh', '-c']
        workload.cmd = ['echo hello']

        const wrapped = wrapEntrypointForLogCapture(workload)

        expect(wrapped!.slice(-3)).toEqual(['/bin/sh', '-c', 'echo hello'])
    })

    it('returns null when no entrypoint is defined, since the image default cannot be wrapped', () => {
        const workload = new Workload('service', 'alpine:latest')
        workload.cmd = ['serve']

        expect(wrapEntrypointForLogCapture(workload)).toBeNull()
    })
})
