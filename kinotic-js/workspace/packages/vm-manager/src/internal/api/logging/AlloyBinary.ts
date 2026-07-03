import { chmodSync, existsSync, mkdirSync, renameSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

/**
 * Alloy release installed when no binary is found via {@link AlloyBinaryOptions.explicitPath}
 * or the PATH. Override per environment with KINOTIC_ALLOY_VERSION.
 */
export const DEFAULT_ALLOY_VERSION = 'v1.17.1'

export interface AlloyBinaryOptions {
    /** Use exactly this binary; fails if it does not exist. */
    explicitPath?: string
    /** Release tag to download when no binary is found (e.g. v1.17.1). */
    version: string
    /** Directory downloads are installed under, keyed by version. */
    installDir: string
}

/**
 * Maps a Node platform/arch pair to the grafana/alloy release asset name.
 * @throws Error for platforms without a published Alloy binary
 */
export function alloyAssetName(platform: NodeJS.Platform, arch: string): string {
    const os = platform === 'linux' ? 'linux' : platform === 'darwin' ? 'darwin' : null
    const cpu = arch === 'x64' ? 'amd64' : arch === 'arm64' ? 'arm64' : null
    if (!os || !cpu) {
        throw new Error(`No Alloy release asset for platform ${platform}/${arch}`)
    }
    return `alloy-${os}-${cpu}`
}

/**
 * Resolves the Alloy binary to run: the explicit path if given, else `alloy` on the PATH,
 * else a per-version install under {@link AlloyBinaryOptions.installDir}, downloading the
 * release asset from GitHub on first use.
 */
export async function resolveAlloyBinary(options: AlloyBinaryOptions): Promise<string> {
    if (options.explicitPath) {
        if (!existsSync(options.explicitPath)) {
            throw new Error(`KINOTIC_ALLOY_PATH does not exist: ${options.explicitPath}`)
        }
        return options.explicitPath
    }

    const onPath = Bun.which('alloy')
    if (onPath) {
        return onPath
    }

    const installed = join(options.installDir, options.version, 'alloy')
    if (existsSync(installed)) {
        return installed
    }
    return downloadAlloy(options.version, join(options.installDir, options.version))
}

async function downloadAlloy(version: string, versionDir: string): Promise<string> {
    const asset = alloyAssetName(process.platform, process.arch)
    const url = `https://github.com/grafana/alloy/releases/download/${version}/${asset}.zip`
    console.log(`Downloading Alloy ${version} from ${url}`)

    mkdirSync(versionDir, { recursive: true })
    const zipPath = join(versionDir, `${asset}.zip`)
    const response = await fetch(url)
    if (!response.ok) {
        throw new Error(`Alloy download failed: HTTP ${response.status} for ${url}`)
    }
    await Bun.write(zipPath, response)

    const unzip = spawnSync('unzip', ['-o', zipPath, '-d', versionDir], { stdio: 'ignore' })
    if (unzip.error || unzip.status !== 0) {
        throw new Error(`Failed to unzip ${zipPath} — is 'unzip' installed?`)
    }
    rmSync(zipPath, { force: true })

    // The zip contains a single binary named after the asset
    const binaryPath = join(versionDir, 'alloy')
    renameSync(join(versionDir, asset), binaryPath)
    chmodSync(binaryPath, 0o755)
    console.log(`Alloy installed at ${binaryPath}`)
    return binaryPath
}
