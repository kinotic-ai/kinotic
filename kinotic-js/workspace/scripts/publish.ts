import { spawnSync } from 'child_process'
import { readdirSync, readFileSync, rmSync, existsSync } from 'fs'
import { resolve } from 'path'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

const registry = (process.env.npm_config_registry ?? 'https://registry.npmjs.org').replace(/\/$/, '')

// True if name@version is already on the registry. Lets the publish loop skip versions that are
// already out, instead of failing the whole run trying to publish over an existing version.
async function isAlreadyPublished(name: string, version: string): Promise<boolean> {
    try {
        const res = await fetch(`${registry}/${name.replace('/', '%2F')}`)
        if (!res.ok) return false
        const data = await res.json() as { versions?: Record<string, unknown> }
        return Boolean(data.versions?.[version])
    } catch {
        return false
    }
}

// Delete bun.lock to ensure a fresh install
const lockFile = resolve(root, 'bun.lock')
if (existsSync(lockFile)) {
    console.log('Deleting bun.lock...')
    rmSync(lockFile)
}

// Fresh install
console.log('Running bun install...')
const installResult = spawnSync('bun', ['install'], { cwd: root, stdio: 'inherit' })
if (installResult.status !== 0) {
    console.error('bun install failed')
    process.exit(1)
}

// Build
console.log('\nRunning bun build...')
const buildResult = spawnSync('bun', ['run', 'build'], { cwd: root, stdio: 'inherit' })
if (buildResult.status !== 0) {
    console.error('bun build failed')
    process.exit(1)
}

const packages = readdirSync(packagesDir)
    .filter(dir => {
        try {
            const pkg = JSON.parse(readFileSync(resolve(packagesDir, dir, 'package.json'), 'utf-8'))
            return !pkg.private
        } catch {
            return false
        }
    })
    .map(dir => `packages/${dir}`)

console.log(`Publishing ${packages.length} packages...`)

let failed = false

for (const pkg of packages) {
    const pkgJson = JSON.parse(readFileSync(resolve(root, pkg, 'package.json'), 'utf-8'))

    if (await isAlreadyPublished(pkgJson.name, pkgJson.version)) {
        console.log(`\nSkipping ${pkgJson.name}@${pkgJson.version} — already on the registry`)
        continue
    }

    console.log(`\nPublishing ${pkgJson.name}@${pkgJson.version}...`)

    const isBeta = pkgJson.version.includes('beta')
    const publishArgs = isBeta ? ['publish', '--tag', 'beta'] : ['publish']

    const result = spawnSync('bun', publishArgs, {
        cwd: resolve(root, pkg),
        stdio: 'inherit',
    })

    if (result.status !== 0) {
        console.error(`Failed to publish ${pkgJson.name}`)
        failed = true
    }
}

if (failed) {
    process.exit(1)
}
