import type { Identifiable } from '@kinotic-ai/core'

export class Application implements Identifiable<string> {
    public id: string
    public description: string
    public updated: number | null = null

    constructor(id: string, description: string) {
        this.id = id
        this.description = description
    }

}
