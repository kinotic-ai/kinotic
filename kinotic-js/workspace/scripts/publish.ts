import { spawnSync } from 'child_process'
import { readdirSync, readFileSync, rmSync, existsSync } from 'fs'
import { resolve } from 'path'
import { runInThisContext } from 'vm'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

// `bun run publish --skip-tests` skips the verification step, for when manually
// republishing many artifacts where the suite has already been run.
const skipTests = process.argv.includes('--skip-tests')

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
