import type { KinoticProjectConfig } from '@kinotic-ai/core'

const config: KinoticProjectConfig = {
  application: "e2e-tests",
  entitiesPaths: [
    {
      path: "test/domain",
      repositoryPath: "test/repository",
      mirrorFolderStructure: true
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
