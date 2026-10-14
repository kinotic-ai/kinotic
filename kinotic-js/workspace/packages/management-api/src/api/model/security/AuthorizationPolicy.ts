import type { FunctionDefinition } from '@kinotic-ai/idl'

export enum AuthorizationScopeKind { ORGANIZATION = 'ORGANIZATION', APPLICATION = 'APPLICATION', APPLICATION_TENANT = 'APPLICATION_TENANT' }
export enum AuthorizationSubjectKind { IDENTITY = 'IDENTITY', GROUP = 'GROUP' }
export enum AuthorizationSelector { ALL = 'ALL', EXACT = 'EXACT' }
export enum AuthorizationEffect { ALLOW = 'ALLOW', DENY = 'DENY' }
export interface AuthorizationScope { kind: AuthorizationScopeKind; organizationId: string; applicationId?: string | null; tenantId?: string | null }
export interface AuthorizationPermission { permission: string; resourceType: string; label?: string; tenantDelegable: boolean }
export interface AuthorizationRole { id: string; name: string; permissions: string[] }
export interface AuthorizationGroup { id: string; name: string; memberIds: string[] }
export interface AuthorizationAssignment { id: string; roleId: string; subjectKind: AuthorizationSubjectKind; subjectId: string; resourceType: string; selector: AuthorizationSelector; resourceId?: string | null; effect: AuthorizationEffect }
export interface AuthorizationPolicy { id?: string; scope: AuthorizationScope; revision: number; administrators: string[]; roles: AuthorizationRole[]; groups: AuthorizationGroup[]; assignments: AuthorizationAssignment[] }
export interface AuthorizationPolicyView { policy: AuthorizationPolicy | null; permissions: AuthorizationPermission[]; pending: boolean; modelRevision: number }
export interface ApplicationServiceContract { namespace?: string | null; name: string; zone?: string | null; version: string; functions: FunctionDefinition[] }
export interface AuthorizationIdentityOption { id: string; label: string }
