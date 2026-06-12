import {SpawnEngine} from './api/SpawnEngine'

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

// Assigned to a global rather than exported: GraalJsSpawnRenderer (kinotic-github)
// evaluates dist/spawn-renderer.global.js as a plain script in a GraalJS context
// and looks up KinoticSpawn in the global bindings.
;(globalThis as unknown as Record<string, unknown>)['KinoticSpawn'] = {renderSpawn}
