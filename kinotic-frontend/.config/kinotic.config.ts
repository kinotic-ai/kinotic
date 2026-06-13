import type { KinoticProjectConfig } from '@kinotic-ai/os-api'

const config: KinoticProjectConfig = {
  organization: "kinotic",
  application: "structures__system",
  entitiesPaths: [
    "src/domain"
  ],
  generatedPath: "src/services",
  fileExtensionForImports: ".js",
  validate: false
}

export default config
