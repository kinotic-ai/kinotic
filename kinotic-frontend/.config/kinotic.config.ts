import type { KinoticProjectConfig } from '@kinotic-ai/management-api'

const config: KinoticProjectConfig = {
  organizationId: "kinotic",
  applicationId: "structures__system",
  entitiesPaths: [
    "src/domain"
  ],
  generatedPath: "src/services",
  fileExtensionForImports: ".js",
  validate: false
}

export default config
