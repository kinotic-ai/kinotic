import { spawnSync } from 'child_process'
import { readdirSync, readFileSync } from 'fs'
import { resolve } from 'path'
import { runInThisContext } from 'vm'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

// `bun run publish --skip-tests` skips the verification step, for when manually
// republishing many artifacts where the suite has already been run.
const skipTests = process.argv.includes('--skip-tests')

// `bun run publish --beta` publishes under the npm `beta` dist-tag. The flag and the version
// must agree: --beta requires every package version to be a -beta prerelease, and a -beta
// version cannot be published without the flag — so a stable dist-tag can never end up
// pointing at a beta build, or the reverse.
const beta = process.argv.includes('--beta')

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

// Smoke-tests the built GraalJS bundle the Kinotic server consumes. vitest covers
// the engine source, but not this inlined iife — where a bundler/dep interaction
// can silently break it (e.g. zod tree-shaking). This runs against the exact
// bundle produced by the fresh install above.
async function verifyGraalBundle(): Promise<void> {
    const bundlePath = resolve(packagesDir, 'spawn', 'dist', 'graal-spawn-renderer.js')
    console.log(`Verifying ${bundlePath}...`)
    runInThisContext(readFileSync(bundlePath, 'utf-8'))
    const kinoticSpawn = (globalThis as { KinoticSpawn?: { renderSpawn(input: string): Promise<string> } }).KinoticSpawn
    if (!kinoticSpawn) {
        console.error('Graal bundle did not define KinoticSpawn')
        process.exit(1)
    }
    const rendered = JSON.parse(await kinoticSpawn.renderSpawn(JSON.stringify({
        files: { 'a.txt.liquid': '{{ name | upperFirst }}', 'spawn.json': '{}' },
        context: { name: 'verify' },
    })))
    if (rendered.files['a.txt'] !== 'Verify') {
        console.error(`Graal bundle smoke test failed: ${JSON.stringify(rendered.files)}`)
        process.exit(1)
    }
    console.log('Graal bundle OK')
}

// Install from the lockfile so the publish builds against the same resolution as development
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

const published: Pkg[] = []
const failedToPublish: Pkg[] = []

for (const pkg of toPublish) {
    const isBetaVersion = pkg.version.includes('-beta.')
    if (beta && !isBetaVersion) {
        console.error(`--beta requires a -beta prerelease version, but ${pkg.name} is ${pkg.version}`)
        process.exit(1)
    }
    if (!beta && isBetaVersion) {
        console.error(`${pkg.name}@${pkg.version} is a beta prerelease; pass --beta to publish it under the beta dist-tag`)
        process.exit(1)
    }
}

if (toPublish.length > 0) {
    // Verify only the packages being published, so e.g. a spawn-only publish doesn't
    // spin up core's gateway. The GraalJS bundle smoke test runs only when spawn is
    // among them, since that's whose bundle it is. --skip-tests bypasses all of it.
    if (skipTests) {
        console.log('\nSkipping tests (--skip-tests)')
    } else {
        for (const pkg of toPublish) {
            console.log(`\nTesting ${pkg.name}...`)
            const testResult = spawnSync('bun', ['run', '--filter', pkg.name, 'test'], { cwd: root, stdio: 'inherit' })
            if (testResult.status !== 0) {
                console.error(`Tests failed for ${pkg.name}`)
                process.exit(1)
            }
        }
        if (toPublish.some(p => p.name === '@kinotic-ai/spawn')) {
            await verifyGraalBundle()
        }
    }

    for (const pkg of toPublish) {
        console.log(`\nPublishing ${pkg.name}@${pkg.version}...`)

        const publishArgs = beta ? ['publish', '--tag', 'beta'] : ['publish']

        const result = spawnSync('bun', publishArgs, { cwd: pkg.dir, stdio: 'inherit' })

        if (result.status !== 0) {
            console.error(`Failed to publish ${pkg.name}`)
            failedToPublish.push(pkg)
        } else {
            published.push(pkg)
        }
    }
}

// Single end-of-run summary, so the outcome isn't buried under each package's
// verbose bun publish output (file lists, shasums, sizes).
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

