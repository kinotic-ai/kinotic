/// <reference types="vite/client" />

// The build variables helpers.ts reads, typed so this package type-checks as its own project
// (IDE/editor, library build). In an app build these interfaces merge with the app's vite/client types.
interface ImportMetaEnv {
  readonly VITE_KINOTIC_HOST?: string
  readonly VITE_KINOTIC_PORT?: string
  readonly VITE_KINOTIC_USE_SSL?: string
}
