// Models
export * from '@/api/model/QueryParameter'
export * from '@/api/model/QueryOptions'
export * from '@/api/model/TenantSpecificId'

// Decorators
export * from '@/api/idl/EntityServiceDecorator'
export * from '@/api/idl/EntityServiceDecoratorsConfig'
export * from '@/api/idl/EntityServiceDecoratorsDecorator'
export * from '@/api/idl/EntityType'
export * from '@/api/idl/EsIndexConfigurationData'
export * from '@/api/idl/MultiTenancyType'
export * from '@/api/idl/PrecisionType'

// Services
export * from '@/api/IEntitiesService'
export * from '@/api/IAdminEntitiesService'
export * from '@/api/IEntityService'
export * from '@/api/IAdminEntityService'

// Plugin
export * from '@/api/PersistencePlugin'

// Re-export Kinotic core so consumers only need one import
export { Kinotic, KinoticSingleton, type IKinotic, type KinoticPlugin } from '@kinotic-ai/core'

import type { IPersistenceExtension } from '@/api/PersistencePlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends IPersistenceExtension {}
}
