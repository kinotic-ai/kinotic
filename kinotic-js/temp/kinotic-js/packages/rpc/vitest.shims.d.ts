declare module 'vitest' {
    export interface ProvidedContext {
        CONTINUUM_HOST: string
        CONTINUUM_PORT: number
    }
}

export {}
