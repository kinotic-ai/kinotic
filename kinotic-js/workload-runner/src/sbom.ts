import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { ProjectDependencies } from '@kinotic-ai/management-api'

/**
 * The SBOM of a project checkout: the dependency tree of its `bun.lock`, resolved the way bun
 * loads it. Every package the lockfile installs is listed once, by its package URL; the project's
 * own workspace packages are the project, not its dependencies, so they are not listed.
 */

/** The lockfile the SBOM is read from, at the root of the checkout. */
const LOCKFILE = 'bun.lock'

/** The bun.lock formats this reader reads. */
const LOCKFILE_VERSIONS = [1, 2]

/**
 * The version of the tree this reader derives from a lockfile, part of every dependency hash; bump
 * it whenever the tree read from the same lockfile changes, so every project's SBOM is read again.
 */
const TREE_VERSION = 1

/** The dependencies a package or a workspace declares, by name, as bun.lock records them. */
interface Declarations {
    dependencies?: Record<string, string>
    optionalDependencies?: Record<string, string>
    peerDependencies?: Record<string, string>
    /** The peer dependencies loaded only when something else installs them */
    optionalPeers?: string[]
}

/** One of the project's own package.json files. */
interface Workspace extends Declarations {
    name?: string
    devDependencies?: Record<string, string>
}

/** The parts of bun.lock the reader reads. */
interface Lockfile {
    lockfileVersion: number
    /** The project's own package.json files, by directory; the root's is "" */
    workspaces: Record<string, Workspace>
    /**
     * Every package bun installs, by its key: the names of the packages it is installed under,
     * then its own. Each entry starts with the package's `name@resolution`, and its first object
     * holds its declarations.
     */
    packages: Record<string, unknown[]>
}

/** A dependency as bun resolves it: the lockfile key it is loaded from. */
interface Resolved {
    key: string
    /** Whether the dependent loads it only when it is installed: an optional dependency or peer */
    optional: boolean
}

/**
 * A SHA-256 of the checkout's `bun.lock` and of the version of the tree read from it, everything
 * the SBOM is read from, so two checkouts with the same hash have the same SBOM; `null` when the
 * checkout has no `bun.lock`.
 */
export function dependencyHashOf(workspaceDir: string): string | null {
    const lockfile = join(workspaceDir, LOCKFILE)
    let ret: string | null = null
    if (existsSync(lockfile)) {
        ret = createHash('sha256').update(`kinotic-sbom ${TREE_VERSION}\n`).update(readFileSync(lockfile)).digest('hex')
    }
    return ret
}

/**
 * Reads the dependency tree of the checkout's `bun.lock`: every package it installs, which of them
 * the project's package.json files declare, which only development or optional dependencies
 * reach, and which package loads which. Fails on a lockfile version this reader does not read.
 */
export function readDependencies(workspaceDir: string): ProjectDependencies {
    const lockfile = Bun.JSONC.parse(readFileSync(join(workspaceDir, LOCKFILE), 'utf-8')) as Lockfile
    // version 2 lays the lockfile out as version 1 does, adding only integrity checks bun runs when it parses one
    if (!LOCKFILE_VERSIONS.includes(lockfile.lockfileVersion)) {
        throw new Error(`${LOCKFILE} is lockfile version ${lockfile.lockfileVersion}, and the SBOM is read from versions ${LOCKFILE_VERSIONS.join(' and ')}`)
    }
    const purls = new Map<string, string>()
    const loads = new Map<string, Resolved[]>()
    for (const [key, entry] of Object.entries(lockfile.packages)) {
        const [name, resolution] = splitSpec(entry[0] as string)
        if (!resolution.startsWith('workspace:')) {
            purls.set(key, purlOf(name, resolution))
            loads.set(key, resolveAll(lockfile, key, declarationsOf(entry)))
        }
    }

    const direct = new Set<string>()
    const required: string[] = []
    const reachable: string[] = []
    for (const [dir, workspace] of Object.entries(lockfile.workspaces)) {
        // bun installs a member's own versions of its dependencies under the member's name
        const key = dir === '' ? '' : workspace.name ?? ''
        for (const dependency of resolveAll(lockfile, key, workspace)) {
            direct.add(dependency.key)
            reachable.push(dependency.key)
            if (!dependency.optional) {
                required.push(dependency.key)
            }
        }
        for (const dependency of resolveAll(lockfile, key, { dependencies: workspace.devDependencies })) {
            direct.add(dependency.key)
        }
    }
    // a package is runtime when production dependencies reach it through required dependencies
    // alone, optional when they reach it only through optional ones, and development otherwise
    const runtimeKeys = reach(required, loads, false)
    const productionKeys = reach(reachable, loads, true)

    const packages = [...new Set(purls.values())].sort()
    const positions = new Map(packages.map((purl, position) => [purl, position]))
    const positionOf = (key: string) => positions.get(purls.get(key)!)!
    const runtime = new Set([...runtimeKeys].filter(key => purls.has(key)).map(positionOf))
    const production = new Set([...productionKeys].filter(key => purls.has(key)).map(positionOf))
    const edges = new Map<string, [number, number]>()
    for (const [key, dependencies] of loads) {
        for (const dependency of dependencies) {
            if (purls.has(dependency.key)) {
                const edge: [number, number] = [positionOf(key), positionOf(dependency.key)]
                if (edge[0] !== edge[1]) {
                    edges.set(edge.join(','), edge)
                }
            }
        }
    }

    const ret = new ProjectDependencies()
    ret.packages = packages
    ret.direct = [...new Set([...direct].filter(key => purls.has(key)).map(positionOf))].sort((a, b) => a - b)
    ret.development = packages.map((_, position) => position).filter(position => !production.has(position))
    ret.optional = packages.map((_, position) => position).filter(position => production.has(position) && !runtime.has(position))
    ret.edges = [...edges.values()].sort((a, b) => a[0] - b[0] || a[1] - b[1])
    return ret
}

// "name@resolution", where a scoped name carries an @ of its own
function splitSpec(spec: string): [string, string] {
    const at = spec.indexOf('@', 1)
    return [spec.slice(0, at), spec.slice(at + 1)]
}

// pkg:npm/<name>@<resolution>, each part percent-encoded so a scope's @ is %40; a registry package
// resolves to its version, and a package from anywhere else to that source, which no registry
// version equals
function purlOf(name: string, resolution: string): string {
    return `pkg:npm/${name.split('/').map(encodeURIComponent).join('/')}@${encodeURIComponent(resolution)}`
}

function declarationsOf(entry: unknown[]): Declarations {
    const found = entry.slice(1).find(part => typeof part === 'object' && part !== null && !Array.isArray(part))
    return (found ?? {}) as Declarations
}

/**
 * The keys the package or workspace at `key` loads its declared dependencies from; a name the
 * lockfile holds no package for, such as a peer nothing installs, is left out. An optional
 * dependency overrides a regular one of the same name, as it does in npm, and a regular
 * dependency overrides a peer.
 */
function resolveAll(lockfile: Lockfile, key: string, declarations: Declarations): Resolved[] {
    const optionalPeers = new Set(declarations.optionalPeers ?? [])
    const optionalByName = new Map<string, boolean>()
    for (const name of Object.keys(declarations.peerDependencies ?? {})) {
        optionalByName.set(name, optionalPeers.has(name))
    }
    for (const name of Object.keys(declarations.dependencies ?? {})) {
        optionalByName.set(name, false)
    }
    for (const name of Object.keys(declarations.optionalDependencies ?? {})) {
        optionalByName.set(name, true)
    }
    const ret: Resolved[] = []
    for (const [name, optional] of optionalByName) {
        const resolved = resolve(lockfile, key, name)
        if (resolved !== undefined) {
            ret.push({ key: resolved, optional })
        }
    }
    return ret
}

/**
 * The key bun loads dependency `name` of the package at `key` from: the first of `key/name`, each
 * ancestor's `ancestor/name`, and `name` that the lockfile holds.
 */
function resolve(lockfile: Lockfile, key: string, name: string): string | undefined {
    const path = segmentsOf(key)
    let ret: string | undefined
    for (let depth = path.length; depth >= 0 && ret === undefined; depth--) {
        const candidate = [...path.slice(0, depth), name].join('/')
        if (Object.hasOwn(lockfile.packages, candidate)) {
            ret = candidate
        }
    }
    return ret
}

// A key's segments are package names, and a scoped name spans two parts of the path
function segmentsOf(key: string): string[] {
    const parts = key === '' ? [] : key.split('/')
    const segments: string[] = []
    for (let i = 0; i < parts.length; i++) {
        segments.push(parts[i]!.startsWith('@') && i + 1 < parts.length ? `${parts[i]}/${parts[++i]}` : parts[i]!)
    }
    return segments
}

/** Every key reached from `starts`, following optional dependencies only when `throughOptional` is set. */
function reach(starts: string[], loads: Map<string, Resolved[]>, throughOptional: boolean): Set<string> {
    const reached = new Set<string>()
    const pending = [...starts]
    while (pending.length > 0) {
        const key = pending.pop()!
        if (!reached.has(key)) {
            reached.add(key)
            for (const dependency of loads.get(key) ?? []) {
                if (throughOptional || !dependency.optional) {
                    pending.push(dependency.key)
                }
            }
        }
    }
    return reached
}
