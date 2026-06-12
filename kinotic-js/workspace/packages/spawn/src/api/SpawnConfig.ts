import {z} from 'zod'

/**
 * Declares a single property a spawn needs to render, as found in a
 * spawn.json propertySchema. Descriptions and defaults may contain liquid
 * expressions, which are rendered against the context resolved so far before
 * the property is requested from a PropertyResolver.
 */
export interface PropertySchema {
  type?: 'string' | 'number' | 'integer' | 'boolean'
  description?: string
  default?: string | number | boolean
  enum?: string[]
}

/**
 * The contents of a spawn.json file: an optional inherited spawn ref, globals
 * made available to all templates, and the schema of properties the spawn
 * requires.
 */
export interface SpawnConfig {
  inherits?: string
  globals?: Record<string, unknown>
  propertySchema?: Record<string, PropertySchema>
}

export const PropertySchemaSchema: z.ZodType<PropertySchema> = z.object({
  type: z.enum(['string', 'number', 'integer', 'boolean']).optional(),
  description: z.string().optional(),
  default: z.union([z.string(), z.number(), z.boolean()]).optional(),
  enum: z.array(z.string()).optional(),
})

export const SpawnConfigSchema: z.ZodType<SpawnConfig> = z.object({
  inherits: z.string().optional(),
  globals: z.record(z.string(), z.unknown()).optional(),
  propertySchema: z.record(z.string(), PropertySchemaSchema).optional(),
})

export type GlobalsType = Record<string, unknown>
export type PropertySchemaType = Record<string, PropertySchema>
