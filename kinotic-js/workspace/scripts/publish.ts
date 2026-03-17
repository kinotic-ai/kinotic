import { spawnSync } from 'child_process'
import { readdirSync, readFileSync } from 'fs'
import { resolve } from 'path'

const root = process.cwd()
const packagesDir = resolve(root, 'packages')

const { version } = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf-8'))
const isBeta = version.includes('beta')
const publishArgs = isBeta ? ['publish', '--tag', 'beta'] : ['publish']

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

console.log(`Publishing version ${version}${isBeta ? ' [beta]' : ''} for ${packages.length} packages...`)

let failed = false

for (const pkg of packages) {
    const pkgJson = JSON.parse(readFileSync(resolve(root, pkg, 'package.json'), 'utf-8'))

    console.log(`\nPublishing ${pkgJson.name}@${pkgJson.version}...`)

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
