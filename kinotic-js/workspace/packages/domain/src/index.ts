export * from '@/api/domain/Application'
export * from '@/api/domain/Project'
export * from '@/api/domain/ProjectType'
export * from '@/api/IApplicationService'
export * from '@/api/IProjectService'
export * from '@/api/DomainPlugin'

import type { IDomainExtension } from '@/api/DomainPlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends IDomainExtension {}
}
