import { describe, expect, it } from 'bun:test'
import { alloyAssetName, resolveAlloyBinary } from '@/internal/api/logging/AlloyBinary'

describe('alloyAssetName', () => {

    it('maps node platform/arch pairs to release asset names', () => {
        expect(alloyAssetName('linux', 'x64')).toBe('alloy-linux-amd64')
        expect(alloyAssetName('linux', 'arm64')).toBe('alloy-linux-arm64')
        expect(alloyAssetName('darwin', 'x64')).toBe('alloy-darwin-amd64')
        expect(alloyAssetName('darwin', 'arm64')).toBe('alloy-darwin-arm64')
    })

    it('throws for platforms without a published binary', () => {
        expect(() => alloyAssetName('win32', 'x64')).toThrow('No Alloy release asset')
        expect(() => alloyAssetName('linux', 'ia32')).toThrow('No Alloy release asset')
    })
})

describe('resolveAlloyBinary', () => {

    it('fails fast when the explicit path does not exist', async () => {
        await expect(resolveAlloyBinary({
            explicitPath: '/nonexistent/alloy',
            version: 'v1.17.1',
            installDir: '/tmp/unused',
        })).rejects.toThrow('KINOTIC_ALLOY_PATH does not exist')
    })
})
