import {SpawnEngine} from './api/SpawnEngine'

/**
 * Entry point for dist/spawn-renderer.global.js, the self-contained bundle
 * produced by this package's build:renderer script. It exists for the Kinotic
 * server, not for JavaScript consumers — nothing here is exported from
 * index.ts.
 *
 * GraalJsSpawnRenderer (kinotic-github) extracts the bundle from this
 * package's npm tarball at build time, evaluates it as a plain script in an
 * embedded GraalJS context, and calls globalThis.KinoticSpawn.renderSpawn so
 * the server renders project baselines with exactly the engine the CLI uses.
 * GraalJS provides no module system and no Node APIs, which is why the bundle
 * is a single browser-targeted IIFE and the contract is JSON strings in and
 * out.
 */

const engine = new SpawnEngine()

/**
 * Renders the spawn described by a JSON string of {files, context} and
 * resolves to the rendered files as a JSON string. A property required by the
 * spawn but missing from the context rejects the promise.
 */
function renderSpawn(inputJson: string): Promise<string> {
  const input = JSON.parse(inputJson) as {files: Record<string, string>, context: Record<string, unknown>}
  return engine.renderSpawn(input.files, {context: input.context})
    .then(result => JSON.stringify(result.files))
}

// Assigned to a global rather than exported: the Java side evaluates the
// bundle as a plain script and looks up KinoticSpawn in the global bindings.
;(globalThis as unknown as Record<string, unknown>)['KinoticSpawn'] = {renderSpawn}
