import type { IParticipant } from './IParticipant'

/**
 * Created by Navid Mitchell on 6/2/20
 */
export class Participant implements IParticipant {

    public id: string;

    public tenantId?: string | null;

    public authScopeType?: string | null;

    public authScopeId?: string | null;

    public metadata: Map<string, string>;

    public roles: string[];

    constructor(id: string,
                tenantId?: string,
                authScopeType?: string,
                authScopeId?: string,
                metadata?: Map<string, string>,
                roles?: string[]) {
        this.id = id
        this.tenantId = tenantId;
        this.authScopeType = authScopeType;
        this.authScopeId = authScopeId;
        this.metadata = metadata || new Map();
        this.roles = roles || [];
    }

}
