import type { KinoticProjectConfig } from '@kinotic-ai/os-api'

const config: KinoticProjectConfig = {
  organization: "kinotic-test",
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
