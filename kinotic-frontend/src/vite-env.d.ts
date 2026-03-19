/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_CONTINUUM_HOST?: string
  readonly VITE_CONTINUUM_PORT?: string
  readonly VITE_CONTINUUM_USE_SSL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
