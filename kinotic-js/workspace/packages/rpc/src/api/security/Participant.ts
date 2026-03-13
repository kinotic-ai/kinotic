import type { IParticipant } from './IParticipant'

/**
 * Created by Navid Mitchell on 6/2/20
 */
export class Participant implements IParticipant {

    public id: string;

    public tenantId?: string | null;

    public metadata: Map<string, string>;

    public roles: string[];

    constructor(id: string,
                tenantId?: string,
                metadata?: Map<string, string>,
                roles?: string[]) {
        this.id = id
        this.tenantId = tenantId;
        this.metadata = metadata || new Map();
        this.roles = roles || [];
    }

}
