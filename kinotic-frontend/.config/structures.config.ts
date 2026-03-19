

import type { TypescriptProjectConfig } from '@kinotic/structures-api'

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
