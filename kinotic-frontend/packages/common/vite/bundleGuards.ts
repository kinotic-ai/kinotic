const COMMON_PACKAGE = '@kinotic-ai/frontend-common'

interface GuardContext {
    error(message: string): never
}

interface BundleEntry {
    type: string
    imports?: string[]
}

/**
 * Fails a production build on the bundle shapes that let a chunk run before a chunk it depends on
 * has finished evaluating, which surfaces only in the browser as an undefined import and a blank
 * page:
 *
 * - a dynamic import of `@kinotic-ai/frontend-common`'s index, which makes the index chunk
 *   statically import every chunk holding one of its exports
 * - a cycle of static imports between output chunks
 */
export function bundleGuards() {
    return {
        name: 'kinotic-bundle-guards',
        apply: 'build' as const,

        resolveDynamicImport(this: GuardContext, source: string, importer: string | undefined) {
            if (source === COMMON_PACKAGE) {
                this.error(`${importer} imports ${COMMON_PACKAGE} dynamically. Import from it statically: its index `
                           + `chunk is part of every app's startup graph, so a dynamic import loads nothing later and `
                           + `makes that chunk import every chunk that holds one of its exports.`)
            }
            return null
        },

        generateBundle(this: GuardContext, _options: unknown, bundle: Record<string, BundleEntry>) {
            const cycle = findChunkCycle(bundle)
            if (cycle) {
                this.error(`Output chunks import each other in a cycle: ${cycle.join(' -> ')}. Whichever chunk loads `
                           + `first runs with the others' exports still undefined.`)
            }
        },
    }
}

// Depth-first search over the chunks' static imports; returns the first cycle found, closed on its first chunk
function findChunkCycle(bundle: Record<string, BundleEntry>): string[] | null {
    const done = new Set<string>()
    const path: string[] = []
    let ret: string[] | null = null

    const visit = (chunk: string) => {
        const onPath = path.indexOf(chunk)
        if (onPath >= 0) {
            ret = [...path.slice(onPath), chunk]
        } else if (!done.has(chunk)) {
            path.push(chunk)
            for (const imported of bundle[chunk]?.imports ?? []) {
                if (ret) break
                if (bundle[imported]?.type === 'chunk') visit(imported)
            }
            path.pop()
            done.add(chunk)
        }
    }

    for (const [fileName, entry] of Object.entries(bundle)) {
        if (ret) break
        if (entry.type === 'chunk') visit(fileName)
    }
    return ret
}
