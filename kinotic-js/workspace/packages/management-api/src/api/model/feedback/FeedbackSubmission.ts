import { FeedbackType } from '@/api/model/feedback/FeedbackType'

/**
 * A bug report or feature request about Kinotic, as its sender writes it. Every field is published
 * in a public GitHub issue.
 */
export class FeedbackSubmission {

    /**
     * Whether this reports a bug or requests a feature.
     */
    public type: FeedbackType = FeedbackType.BUG_REPORT

    /**
     * One line naming the problem or the request, at most 200 characters.
     */
    public title: string = ''

    /**
     * What went wrong, or what is wanted and why, in Markdown.
     */
    public description: string = ''

    /**
     * For a bug, the steps that make it happen; optional.
     */
    public stepsToReproduce: string | null = null

    /**
     * For a bug, what should have happened; optional.
     */
    public expectedBehavior: string | null = null

    /**
     * For a bug, what happened instead, such as the error message returned; optional.
     */
    public actualBehavior: string | null = null

    /**
     * Where in Kinotic the sender was, such as the portal page's route or the MCP tool that failed;
     * optional.
     */
    public location: string | null = null

}
