declare module 'vitest' {
    export interface ProvidedContext {
        KINOTIC_HOST: string
        KINOTIC_PORT: number
        KINOTIC_OPENAPI_PORT: number
    }
}

export {}
