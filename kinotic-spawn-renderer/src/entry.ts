import {SpawnEngine} from '@kinotic-ai/spawn'

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
