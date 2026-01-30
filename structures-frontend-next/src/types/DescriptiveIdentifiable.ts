import type { Identifiable } from '@kinotic/continuum-client'

export interface DescriptiveIdentifiable extends Identifiable<string> {
    description?: string
    name?: string
    [key: string]: any
} 