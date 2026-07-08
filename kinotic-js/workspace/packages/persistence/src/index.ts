// Models
export * from '@/api/model/QueryParameter'
export * from '@/api/model/QueryOptions'
export * from '@/api/model/TenantSpecificId'

// Decorators
export * from '@/api/KinoticPersistenceDecorators'
export * from '@/api/idl/EntityServiceDecorator'
export * from '@/api/idl/EntityServiceDecoratorsConfig'
export * from '@/api/idl/EntityServiceDecoratorsDecorator'
export * from '@/api/idl/EntityType'
export * from '@/api/idl/EsIndexConfigurationData'
export * from '@/api/idl/MultiTenancyType'
export * from '@/api/idl/PolicyDecorator'
export * from '@/api/idl/PrecisionType'
export * from '@/api/idl/RoleDecorator'

// Repositories
export * from '@/api/IEntitiesRepository'
export * from '@/api/IAdminEntitiesRepository'
export * from '@/api/IEntityRepository'
export * from '@/api/IAdminEntityRepository'

// Plugin
export * from '@/api/PersistencePlugin'

import type { IPersistenceExtension } from '@/api/PersistencePlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends IPersistenceExtension {}
}
