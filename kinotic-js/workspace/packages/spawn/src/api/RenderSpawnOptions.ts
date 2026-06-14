import type {PropertyResolver} from './PropertyResolver'
import type {SpawnTree} from './SpawnTree'

/**
 * Options for {@link SpawnEngine#renderSpawn}.
 */
export interface RenderSpawnOptions {

  /**
   * Values made available to the templates. Entries here override globals
   * declared in spawn.json and are never re-requested from the
   * {@link PropertyResolver}.
   */
  context?: Record<string, unknown>

  /**
   * Supplies values for propertySchema entries missing from the context.
   * When omitted, a missing property fails the render.
   */
  propertyResolver?: PropertyResolver

  /**
   * Loads the spawn referenced by a spawn.json {@code inherits} value. The ref
   * is passed verbatim; resolving it (e.g. against the location of the spawn
   * that declared it) is the host's responsibility. Inheritance chains are
   * linear, so refs are requested in declaration order: each call refers to the
   * spawn loaded by the previous one. Required when the spawn uses inheritance.
   */
  loadInherited?: (ref: string) => Promise<SpawnTree>

}
