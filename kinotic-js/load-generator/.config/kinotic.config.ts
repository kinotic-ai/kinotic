import type { KinoticProjectConfig } from '@kinotic-ai/os-api'

const config: KinoticProjectConfig = {
  organization: "kinotic-test",
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
