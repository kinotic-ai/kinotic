/**
 * Declares a single property a spawn needs to render, as found in a
 * spawn.json propertySchema. Descriptions and defaults may contain liquid
 * expressions, which are rendered against the context resolved so far before
 * the property is requested from a {@link PropertyResolver}.
 */
export interface PropertySchema {
  type?: 'string' | 'number' | 'integer' | 'boolean'
  description?: string
  default?: string | number | boolean
  enum?: string[]
}
