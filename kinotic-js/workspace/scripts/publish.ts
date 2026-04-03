import { spawnSync } from 'child_process'
import { readdirSync, readFileSync, rmSync, existsSync } from 'fs'
import { resolve } from 'path'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

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
