import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import { InviteEmailTemplate } from '@/api/model/InviteEmailTemplate'

/**
 * CRUD service for an application's customized invitation email — at most one
 * {@link InviteEmailTemplate} per application. Saving validates the Handlebars sources, so
 * a broken template is rejected instead of breaking the next send. Deleting the template
 * reverts the application to the built-in invitation email.
 */
export interface IInviteEmailTemplateService extends ICrudServiceProxy<InviteEmailTemplate> {

    /**
     * Finds the application's invitation template, or null when the application uses the
     * built-in email.
     */
    findByApplication(applicationId: string): Promise<InviteEmailTemplate | null>

}

export class InviteEmailTemplateService extends CrudServiceProxy<InviteEmailTemplate> implements IInviteEmailTemplateService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('os_api.org.kinotic.os.api.services.InviteEmailTemplateService'))
    }

    public findByApplication(applicationId: string): Promise<InviteEmailTemplate | null> {
        return this.serviceProxy.invoke('findByApplication', [applicationId])
    }

}
