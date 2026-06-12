import type {PropertySchema} from './SpawnConfig'

/**
 * Supplies values for properties declared in a spawn's propertySchema that are
 * absent from the render context. An interactive host can prompt the user; a
 * non-interactive host can omit the resolver entirely, in which case rendering
 * fails on the first missing property.
 */
export interface PropertyResolver {

  /**
   * Returns the value to use for {@code key}.
   *
   * @param key the property name as declared in the propertySchema
   * @param schema the declaration for the property (type, enum, etc.)
   * @param message the property description with any liquid expressions already
   *        rendered against the context resolved so far, or the key when the
   *        schema has no description
   * @param defaultValue the schema default with any liquid expressions already
   *        rendered, or undefined when the schema has no default
   */
  resolve(key: string, schema: PropertySchema, message: string, defaultValue?: unknown): Promise<unknown>

}
