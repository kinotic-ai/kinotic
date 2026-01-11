import type { Identifiable } from '@mindignited/continuum-client'

export interface DescriptiveIdentifiable extends Identifiable<string> {
    description?: string
    name?: string
    [key: string]: any
} 