/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_USE_STRUCTURES_DOCKER: boolean
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
