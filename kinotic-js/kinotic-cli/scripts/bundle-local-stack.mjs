// Bundles the stack `kinotic local` runs into dist/local: the docker-compose directory, and the versions this
// release runs, the platform's image tag and the vm-manager release that serves as the local node.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const cliDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repoDir = path.resolve(cliDir, '..', '..')
const outDir = path.join(cliDir, 'dist', 'local')

const kinoticVersion = fs.readFileSync(path.join(repoDir, 'gradle.properties'), 'utf8')
                         .match(/^kinoticVersion=(.+)$/m)[1].trim()
const vmManagerVersion = JSON.parse(fs.readFileSync(
    path.join(repoDir, 'kinotic-js', 'workspace', 'packages', 'vm-manager', 'package.json'), 'utf8')).version

fs.rmSync(outDir, { recursive: true, force: true })
fs.cpSync(path.join(repoDir, 'deployment', 'docker-compose'), path.join(outDir, 'compose'), { recursive: true })
fs.writeFileSync(path.join(outDir, 'stack.json'), JSON.stringify({ kinoticVersion, vmManagerVersion }, null, 2) + '\n')
console.log(`Bundled the local stack: images ${kinoticVersion}, vm-manager ${vmManagerVersion}`)
