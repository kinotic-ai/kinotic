// Minimal ambient typing for the vite-injected import.meta.env, so this package type-checks
// as its own project (IDE/editor) without a vite dependency. In an app build these interfaces
// merge with the app's vite/client types.
interface ImportMetaEnv {
  readonly VITE_KINOTIC_HOST?: string
  readonly VITE_KINOTIC_PORT?: string
  readonly VITE_KINOTIC_USE_SSL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Asset modules the components import; in an app build vite/client provides these instead
// (this file is not part of app programs — nothing imports it, and app tsconfigs don't include it).
declare module '*.png' {
  const src: string
  export default src
}

declare module '*.svg' {
  const src: string
  export default src
}

declare module '*.css' {}

