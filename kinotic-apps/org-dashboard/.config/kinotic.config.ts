import type { KinoticProjectConfig } from '@kinotic-ai/management-api'

// The ids name the Kinotic OS project this repository deploys as. Kinotic OS stamps them
// into the .config/kinotic.config.ts of the repository it provisions for a project, so a
// deployment keeps that file and these values are only for a checkout outside one.
const config: KinoticProjectConfig = {
  organizationId: "my-organization",
  applicationId: "org-dashboard",
  projectId: "org-dashboard-main",
  // The dashboard reads the platform's management services and defines no entities of its own.
  entitiesPaths: [],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
