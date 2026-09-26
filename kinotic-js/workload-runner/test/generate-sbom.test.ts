import { describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'

const GENERATE_SBOM = join(import.meta.dir, '..', 'src', 'generate-sbom.ts')

const COMMIT = 'a'.repeat(40)

describe('generate-sbom entrypoint', () => {

    it('fails naming the variable when the upload URL is not given', () => {
        const result = spawnSync('bun', [GENERATE_SBOM], {
            env: { ...process.env, KINOTIC_SBOM_FILE: `${COMMIT}.cdx.json`, KINOTIC_SBOM_COMMIT: COMMIT },
            encoding: 'utf-8',
        })

        expect(result.status).not.toBe(0)
        expect(result.stderr).toContain('KINOTIC_SBOM_UPLOAD_URL must be set')
    })
})
