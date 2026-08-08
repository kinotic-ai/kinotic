import type { Identifiable } from '@kinotic-ai/core'

export interface DescriptiveIdentifiable extends Identifiable<string> {
    description?: string
    name?: string
    [key: string]: any
} 