// Bundled from the engine's TypeScript source rather than its dist so the
// bundle builds without a prior workspace build. liquidjs and zod resolve from
// this project's node_modules via NODE_PATH (see the build script); their
// versions must match the ones declared by @kinotic-ai/spawn.
import {SpawnEngine} from '../../kinotic-js/workspace/packages/spawn/src/index'

const engine = new SpawnEngine()

/**
 * Entry point invoked by GraalJsSpawnRenderer. Takes the render input as a
 * JSON string of {files, context} and resolves to the rendered files as a
 * JSON string. A property required by the spawn but missing from the context
 * rejects the promise.
 */
export function renderSpawn(inputJson: string): Promise<string> {
  const input = JSON.parse(inputJson) as {files: Record<string, string>, context: Record<string, unknown>}
  return engine.renderSpawn(input.files, {context: input.context})
    .then(result => JSON.stringify(result.files))
}
