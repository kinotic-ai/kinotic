/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_KINOTIC_HOST?: string
  readonly VITE_KINOTIC_PORT?: string
  readonly VITE_KINOTIC_USE_SSL?: string
  readonly VITE_GA_MEASUREMENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
