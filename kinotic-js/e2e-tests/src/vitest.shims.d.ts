declare module 'vitest' {
    export interface ProvidedContext {
        KINOTIC_HOST: string
        KINOTIC_PORT: number
    }
}

export {}
