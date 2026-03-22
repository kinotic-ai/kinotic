

import type { TypescriptProjectConfig } from '@kinotic-ai/persistence'

const config: TypescriptProjectConfig = {
  mdl: "ts",
  application: "structures__system",
  entitiesPaths: [
    "src/domain"
  ],
  generatedPath: "src/services",
  fileExtensionForImports: ".js",
  validate: false
}

export default config
