import type { ExecutionStatus } from '@/api/model/grind/ExecutionStatus'

/**
 * Which JobRuns a query returns. Each field narrows the runs; a field left null matches every run.
 */
export class JobRunFilter {

    /**
     * The organization that owns the runs.
     */
    public organizationId: string | null = null

    /**
     * The application the runs served.
     */
    public applicationId: string | null = null

    /**
     * The project the runs served.
     */
    public projectId: string | null = null

    /**
     * The status the runs are in.
     */
    public status: ExecutionStatus | null = null

    /**
     * The earliest time the runs started, inclusive, as an ISO-8601 timestamp.
     */
    public startedSince: string | null = null

}
