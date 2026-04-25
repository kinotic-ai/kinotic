import type { Identifiable } from '@kinotic-ai/core'
import { AuthType } from '@/api/model/iam/AuthType'

/**
 * Represents an authenticated identity at any scope layer in the IAM system.
 * Each user is scoped to exactly one layer and is unique by email within that scope.
 *
 * - For SYSTEM and ORGANIZATION scopes, {@link tenantId} must be null.
 * - For APPLICATION scopes, {@link tenantId} is required and identifies the
 *   client tenant the user belongs to within the application's data.
 */
export class IamUser implements Identifiable<string> {
    public id: string | null = null
    public email: string = ''
    public displayName: string | null = null
    public authType: AuthType | null = null
    public oidcSubject: string | null = null
    public oidcConfigId: string | null = null
    public authScopeType: string = ''
    public authScopeId: string = ''
    public tenantId: string | null = null
    public enabled: boolean = true
    public created: number | null = null
    public updated: number | null = null
}
