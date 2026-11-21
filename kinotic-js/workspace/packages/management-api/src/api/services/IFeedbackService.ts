import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { FeedbackIssue } from '@/api/model/feedback/FeedbackIssue'
import type { FeedbackSubmission } from '@/api/model/feedback/FeedbackSubmission'

/**
 * Sends bug reports and feature requests about Kinotic itself to the Kinotic team, for people using
 * the portal and for AI agents working through Kinotic's MCP tools alike.
 */
export interface IFeedbackService {

    /**
     * Sends a bug report or feature request about Kinotic to the Kinotic team, who track it as a
     * public GitHub issue in the Kinotic repository. The issue is public, so it must never include
     * secrets, credentials, personal data or the names of the organization's applications and
     * projects. An organization may send ten per hour.
     * @param submission the bug report or feature request
     */
    send(submission: FeedbackSubmission): Promise<FeedbackIssue>

}

export class FeedbackService implements IFeedbackService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.FeedbackService`)
    }

    public send(submission: FeedbackSubmission): Promise<FeedbackIssue> {
        return this.serviceProxy.invoke('send', [submission])
    }

}
