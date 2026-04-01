import type { KinoticProjectConfig } from '@kinotic-ai/core'

const config: KinoticProjectConfig = {
  application: "load-testing",
  entitiesPaths: [
    {
      path: "src/entity/domain/ecommerce",
      repositoryPath: "src/entity/services/ecommerce",
      mirrorFolderStructure: false
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
