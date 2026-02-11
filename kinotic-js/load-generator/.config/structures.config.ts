

import type { TypescriptProjectConfig } from '@kinotic/structures-api'

const config: TypescriptProjectConfig = {
  mdl: "ts",
  application: "load-testing",
  entitiesPaths: [
    "src/entity/domain/ecommerce"
  ],
  generatedPath: "src/entity/services/ecommerce",
  fileExtensionForImports: ".js",
  validate: false
}

export default config
