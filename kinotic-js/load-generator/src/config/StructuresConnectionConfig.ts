export class StructuresConnectionConfig {

    public stucturesUseSsl: boolean
    public structuresHost: string
    public structuresPort?: number


    constructor(stucturesUseSsl: boolean,
                structuresHost: string,
                structuresPort?: number) {
        this.stucturesUseSsl = stucturesUseSsl
        this.structuresHost = structuresHost
        this.structuresPort = structuresPort
    }

    public static fromEnv(): StructuresConnectionConfig {
        const structuresHost = process.env.STRUCTURES_HOST
        if (!structuresHost) {
            throw new Error('STRUCTURES_HOST environment variable is required')
        }
        const structuresPort = process.env.STRUCTURES_PORT ? parseInt( process.env.STRUCTURES_PORT) : undefined
        const stucturesUseSsl = process.env.STRUCTURES_USE_SSL === 'true'

        return new StructuresConnectionConfig(stucturesUseSsl, structuresHost, structuresPort)
    }

    public print(): void {
        console.log(`STRUCTURES_HOST: ${this.structuresHost}`)
        console.log(`STRUCTURES_PORT: ${this.structuresPort}`)
        console.log(`STRUCTURES_USE_SSL: ${this.stucturesUseSsl}`)
    }
}
