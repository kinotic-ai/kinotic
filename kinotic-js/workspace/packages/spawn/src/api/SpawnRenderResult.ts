import type {SpawnTree} from './SpawnTree'

/**
 * The outcome of rendering a spawn.
 */
export interface SpawnRenderResult {

  /**
   * The rendered files keyed by destination path: liquid expressions in paths
   * are rendered, {@code .liquid} suffixes are stripped, and files from derived
   * spawns overwrite same-destination files from inherited spawns.
   */
  files: SpawnTree

  /**
   * The full context the templates were rendered with: merged globals,
   * caller-provided values, and any values supplied by the property resolver.
   */
  context: Record<string, unknown>

}
