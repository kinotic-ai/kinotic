/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_USE_STRUCTURES_DOCKER: boolean
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}

// vite/client stopped declaring `*.vue` modules in Vite 5, so tsc needs this shim to resolve SFC imports.
declare module '*.vue' {
    import type { DefineComponent } from 'vue'
    const component: DefineComponent<{}, {}, any>
    export default component
}
