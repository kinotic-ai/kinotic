import type { KinoticProjectConfig } from '@kinotic-ai/core'

const config: KinoticProjectConfig = {
  application: "load-testing",
  entitiesPaths: [
    {
      path: "src/entity/domain",
      repositoryPath: "src/repository",
      mirrorFolderStructure: true
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
