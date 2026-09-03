import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { validateLabel } from '@kinotic-ai/core'
import type { MicroserviceArtifact, ProjectArtifacts, UiArtifact } from '@kinotic-ai/management-api'

/**
 * Finds the artifacts a project checkout contains, by where each package sits in the tree:
 *
 * - a microservice is a directory directly under `packages/microservices` holding a
 *   `package.json`; its entry is that file's `main`, or `src/main.ts` when it declares none
 * - a UI is a directory directly under `packages/ui` whose `package.json` declares a `build`
 *   script; one without is a library and is left alone
 *
 * An artifact's identity is the unscoped part of the `name` in its `package.json`
 * (`@acme/orders` is `orders`), which must be a single zone label; the directory name never
 * matters. A missing or invalid name, or two artifacts of one kind sharing a name, throws
 * naming the package. Both lists come back ordered by name.
 */

/** The module a microservice starts from when its package.json declares no main. */
const DEFAULT_MICROSERVICE_ENTRY = 'src/main.ts'

/** One package.json read from the checkout: the package's unscoped name, its directory, and the parsed file. */
interface PackageManifest {
    name: string
    dir: string
    json: Record<string, unknown>
}

export function findArtifacts(workspaceDir: string): ProjectArtifacts {
    const microservices: MicroserviceArtifact[] = requireUniqueNames(readManifests(workspaceDir, 'packages/microservices'))
        .map(manifest => ({ name: manifest.name, dir: manifest.dir, entry: entryOf(manifest) }))
    const uis: UiArtifact[] = requireUniqueNames(readManifests(workspaceDir, 'packages/ui').filter(hasBuildScript))
        .map(manifest => ({ name: manifest.name, dir: manifest.dir }))
    return {
        microservices: microservices.sort(byName),
        uis: uis.sort(byName),
    }
}

/** Reads the package.json of every directory directly under `parent`, skipping directories without one. */
function readManifests(workspaceDir: string, parent: string): PackageManifest[] {
    const parentDir = join(workspaceDir, parent)
    if (!existsSync(parentDir)) {
        return []
    }
    const manifests: PackageManifest[] = []
    for (const child of readdirSync(parentDir)) {
        const dir = `${parent}/${child}`
        const packageJson = join(workspaceDir, dir, 'package.json')
        if (statSync(join(parentDir, child)).isDirectory() && existsSync(packageJson)) {
            manifests.push(parseManifest(dir, readFileSync(packageJson, 'utf-8')))
        }
    }
    return manifests
}

function parseManifest(dir: string, content: string): PackageManifest {
    let json: Record<string, unknown>
    try {
        json = JSON.parse(content)
    } catch (error) {
        throw new Error(`Package ${dir} has a package.json that is not valid JSON: ${error instanceof Error ? error.message : String(error)}`)
    }
    const fullName = json.name
    if (typeof fullName !== 'string' || fullName.trim() === '') {
        throw new Error(`Package ${dir} has no name in its package.json`)
    }
    // @scope/name is identified by name; a scope has no meaning to the platform
    const unscoped = fullName.startsWith('@') && fullName.indexOf('/') > 0
        ? fullName.slice(fullName.indexOf('/') + 1)
        : fullName
    // the rule the server applies to the report too: an artifact's name becomes a workload
    // name and a hostname label
    try {
        validateLabel(unscoped)
    } catch (error) {
        throw new Error(`Package ${dir} has the name '${fullName}': ${error instanceof Error ? error.message : String(error)}`)
    }
    return { name: unscoped, dir, json }
}

function entryOf(manifest: PackageManifest): string {
    const main = manifest.json.main
    let ret: string
    if (typeof main === 'string' && main.trim() !== '') {
        // "./src/index.ts" and "src/index.ts" name the same module; the runtime joins the
        // entry onto the package directory
        ret = main.startsWith('./') ? main.slice(2) : main
    } else {
        ret = DEFAULT_MICROSERVICE_ENTRY
    }
    return ret
}

function hasBuildScript(manifest: PackageManifest): boolean {
    const scripts = manifest.json.scripts
    return typeof scripts === 'object' && scripts !== null
        && typeof (scripts as Record<string, unknown>).build === 'string'
        && ((scripts as Record<string, unknown>).build as string).trim() !== ''
}

// Two artifacts of one kind with the same name would be deployed as one, so a collision
// fails the deploy naming both directories
function requireUniqueNames(manifests: PackageManifest[]): PackageManifest[] {
    const dirsByName = new Map<string, string>()
    for (const manifest of manifests) {
        const other = dirsByName.get(manifest.name)
        if (other !== undefined) {
            throw new Error(`Packages ${other} and ${manifest.dir} both have the name '${manifest.name}'`)
        }
        dirsByName.set(manifest.name, manifest.dir)
    }
    return manifests
}

function byName(a: { name: string }, b: { name: string }): number {
    return a.name < b.name ? -1 : a.name > b.name ? 1 : 0
}
