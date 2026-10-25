import { spawnSync } from 'child_process'
import { readdirSync, readFileSync } from 'fs'
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

// Install from the lockfile so the publish builds against the same resolution as development
console.log('Running pnpm install...')
const installResult = spawnSync('pnpm', ['install', '--frozen-lockfile'], { cwd: root, stdio: 'inherit' })
if (installResult.status !== 0) {
    console.error('pnpm install failed')
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

// Decide what to publish before building anything, so the skip notice prints once up front
// instead of scattered between each package's build and publish output.
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

const published: Pkg[] = []
const failedToPublish: Pkg[] = []

for (const pkg of toPublish) {
    console.log(`\nBuilding ${pkg.name}...`)
    const buildResult = spawnSync('pnpm', ['run', 'build:lib'], { cwd: pkg.dir, stdio: 'inherit' })
    if (buildResult.status !== 0) {
        console.error(`Build failed for ${pkg.name}`)
        process.exit(1)
    }
}

for (const pkg of toPublish) {
    console.log(`\nPublishing ${pkg.name}@${pkg.version}...`)

    // a -beta prerelease publishes under the beta dist-tag, so `latest` always points at a stable
    // version. pnpm's git checks require the publish branch, which defaults to master or main; this
    // repository releases from develop.
    const publishArgs = ['publish', '--publish-branch', 'develop', ...(pkg.version.includes('-beta.') ? ['--tag', 'beta'] : [])]

    const result = spawnSync('pnpm', publishArgs, { cwd: pkg.dir, stdio: 'inherit' })

    if (result.status !== 0) {
        console.error(`Failed to publish ${pkg.name}`)
        failedToPublish.push(pkg)
    } else {
        published.push(pkg)
    }
}

// Single end-of-run summary, so the outcome isn't buried under each package's verbose publish output
console.log(`\n${'='.repeat(48)}`)
if (published.length > 0) {
    console.log(`Published ${published.length} package(s):`)
    for (const pkg of published) {
        console.log(`  ✓ ${pkg.name}@${pkg.version}`)
    }
}
if (skipped.length > 0) {
    console.log(`Already on the registry, skipped ${skipped.length} package(s):`)
    for (const pkg of skipped) {
        console.log(`  – ${pkg.name}@${pkg.version}`)
    }
}
if (failedToPublish.length > 0) {
    console.log(`Failed to publish ${failedToPublish.length} package(s):`)
    for (const pkg of failedToPublish) {
        console.log(`  ✗ ${pkg.name}@${pkg.version}`)
    }
}
if (published.length === 0 && failedToPublish.length === 0) {
    console.log('Nothing new to publish.')
}
if (failedToPublish.length > 0) {
    process.exit(1)
}
