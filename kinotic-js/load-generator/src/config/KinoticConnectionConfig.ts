export class KinoticConnectionConfig {

    public kinoticUseSsl: boolean
    public kinoticHost: string
    public kinoticPort?: number


    constructor(stucturesUseSsl: boolean,
                structuresHost: string,
                structuresPort?: number) {
        this.kinoticUseSsl = stucturesUseSsl
        this.kinoticHost = structuresHost
        this.kinoticPort = structuresPort
    }

    public static fromEnv(): KinoticConnectionConfig {
        const structuresHost = process.env.KINOTIC_HOST
        if (!structuresHost) {
            throw new Error('KINOTIC_HOST environment variable is required')
        }
        const structuresPort = process.env.KINOTIC_PORT ? parseInt( process.env.KINOTIC_PORT) : undefined
        const stucturesUseSsl = process.env.KINOTIC_USE_SSL === 'true'

        return new KinoticConnectionConfig(stucturesUseSsl, structuresHost, structuresPort)
    }

    public print(): void {
        console.log(`KINOTIC_HOST: ${this.kinoticHost}`)
        console.log(`KINOTIC_PORT: ${this.kinoticPort}`)
        console.log(`KINOTIC_USE_SSL: ${this.kinoticUseSsl}`)
    }
}
