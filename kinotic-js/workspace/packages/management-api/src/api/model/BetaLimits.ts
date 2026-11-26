/**
 * The free Kinotic beta cloud plan an organization is on: what it may create while Kinotic Cloud is in
 * beta, how much of it the organization uses, and the plan explained in a few sentences for the person
 * using it.
 */
export class BetaLimits {

    /**
     * The plan in a few sentences, naming each limit, for showing to the person before they plan an
     * application.
     */
    public summary: string = ''

    /** The most applications the organization may have, or null for no cap. */
    public maxApplicationsPerOrganization: number | null = null

    /** The most microservices one application may run across all of its projects, or null for no cap. */
    public maxMicroservicesPerApplication: number | null = null

    /** How many applications the organization has. */
    public applicationCount: number = 0

    /**
     * How many microservices each of the organization's applications runs, by application id. An
     * application that runs none is absent.
     */
    public microservicesByApplication: Record<string, number> = {}

}
