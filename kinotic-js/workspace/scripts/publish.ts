import { spawnSync } from 'child_process'
import { readdirSync, readFileSync, rmSync, existsSync } from 'fs'
import { resolve } from 'path'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

const registry = (process.env.npm_config_registry ?? 'https://registry.npmjs.org').replace(/\/$/, '')

// True if name@version is already on the registry, so the script can skip it instead of failing
// when it tries to publish over an existing version.
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

type Pkg = { dir: string, name: string, version: string }

const packages: Pkg[] = readdirSync(packagesDir)
    .sort()
    .map(dir => {
        try {
            const pkg = JSON.parse(readFileSync(resolve(packagesDir, dir, 'package.json'), 'utf-8'))
            return pkg.private ? null : { dir: resolve(packagesDir, dir), name: pkg.name, version: pkg.version }
        } catch {
            return null
        }
    })
    .filter((p): p is Pkg => p !== null)

// Decide what to publish before publishing anything, so the skip notice prints once up front
// instead of scattered between each package's publish output.
console.log(`\nChecking ${packages.length} packages...`)
const toPublish: Pkg[] = []
const skipped: Pkg[] = []
for (const pkg of packages) {
    if (await isAlreadyPublished(pkg.name, pkg.version)) {
        skipped.push(pkg)
    } else {
        toPublish.push(pkg)
    }
}

if (skipped.length > 0) {
    console.log(`Already on the registry, skipping: ${skipped.map(p => `${p.name}@${p.version}`).join(', ')}`)
}

if (toPublish.length === 0) {
    console.log('Nothing new to publish.')
    process.exit(0)
}

let failed = false

for (const pkg of toPublish) {
    console.log(`\nPublishing ${pkg.name}@${pkg.version}...`)

    const publishArgs = pkg.version.includes('beta') ? ['publish', '--tag', 'beta'] : ['publish']

    const result = spawnSync('bun', publishArgs, { cwd: pkg.dir, stdio: 'inherit' })

    if (result.status !== 0) {
        console.error(`Failed to publish ${pkg.name}`)
        failed = true
    }
}

if (failed) {
    process.exit(1)
}
