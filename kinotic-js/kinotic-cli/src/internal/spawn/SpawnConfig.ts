import {z} from 'zod'

export const PropertySchemaSchema = z.object({
  type: z.enum(['string', 'number', 'integer', 'boolean']).optional(),
  description: z.string().optional(),
  default: z.union([z.string(), z.number(), z.boolean()]).optional(),
  enum: z.array(z.string()).optional(),
})

export const SpawnConfigSchema = z.object({
  inherits: z.string().optional(),
  globals: z.record(z.string(), z.unknown()).optional(),
  propertySchema: z.record(z.string(), PropertySchemaSchema).optional(),
})

export type PropertySchema = z.infer<typeof PropertySchemaSchema>
export type SpawnConfig = z.infer<typeof SpawnConfigSchema>
export type GlobalsType = Record<string, unknown>
export type PropertySchemaType = Record<string, PropertySchema>
