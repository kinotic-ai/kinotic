/**
 * A grind job the platform's operators can start on demand, as the console lists it.
 */
export interface SystemJobDescriptor {

    /**
     * The name identifying the job, which is also the name recorded on each of its runs.
     */
    readonly name: string

    /**
     * The version of the job's definition, recorded on each of its runs.
     */
    readonly version: string | null

    /**
     * What the job does.
     */
    readonly description: string | null

}
