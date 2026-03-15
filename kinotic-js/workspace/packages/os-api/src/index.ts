export * from '@/api/model/Application'
export * from '@/api/model/Project'
export * from '@/api/model/ProjectType'
export * from '@/api/services/IApplicationService'
export * from '@/api/services/ILogManager'
export * from '@/api/services/IProjectService'
export * from '@/api/services/LogManager'
export * from '@/api/OsApiPlugin.js'

import type { IOsApiExtension } from '@/api/OsApiPlugin.js'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends IOsApiExtension {}
}
