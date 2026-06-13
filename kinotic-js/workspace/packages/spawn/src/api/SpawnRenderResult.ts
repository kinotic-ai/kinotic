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
   * The originating spawn path for each rendered file, keyed by the same
   * destination path used in {@link files}. Lets a host trace a rendered file
   * back to its template entry to recover source metadata (e.g. the executable
   * bit) that the destination path alone cannot convey.
   */
  sources: Record<string, string>

  /**
   * The full context the templates were rendered with: merged globals,
   * caller-provided values, and any values supplied by the property resolver.
   */
  context: Record<string, unknown>

}
