import type { KinoticProjectConfig } from '@kinotic-ai/core'

const config: KinoticProjectConfig = {
  application: "load-testing",
  entitiesPaths: [
    "src/entity/domain/ecommerce"
  ],
  generatedPath: "src/entity/services/ecommerce",
  fileExtensionForImports: ".js",
  validate: false
}

export default config
