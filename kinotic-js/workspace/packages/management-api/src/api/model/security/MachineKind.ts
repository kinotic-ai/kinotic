/**
 * What a MachineParticipantIdentity is for: the role of the process that holds its credential.
 */
export enum MachineKind {
    /**
     * A caller someone provisioned by hand: a SYSTEM-scope platform daemon such as the
     * vm-manager, or an APPLICATION-scope API client of one application.
     */
    CLIENT = 'CLIENT',
    /**
     * The sync workload of a project's deployment, which synchronizes the project's entity
     * definitions and reports its artifacts. ORGANIZATION scope.
     */
    PROJECT_SYNC = 'PROJECT_SYNC',
    /**
     * The runtime workload of one of a project's microservices, which publishes the project's
     * services into its application's zone. ORGANIZATION scope.
     */
    APP_RUNTIME = 'APP_RUNTIME'
}
