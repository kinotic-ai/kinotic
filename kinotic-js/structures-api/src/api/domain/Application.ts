import { Identifiable } from '@kinotic/continuum-client'
export class Application implements Identifiable<string> {
    public id: string
    public description: string
    public updated: number | null = null;
    public enableGraphQL: boolean = false
    public enableOpenAPI: boolean = false

    constructor(id: string, description: string) {
        this.id = id;
        this.description = description;
    }

}
