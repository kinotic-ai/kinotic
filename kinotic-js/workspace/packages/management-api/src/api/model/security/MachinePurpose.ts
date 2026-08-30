/**
 * Why a {@link MachineParticipantIdentity} exists. Every purpose other than
 * {@link API_ACCESS} is platform-managed: the platform provisions and rotates the machine,
 * and it is not editable through the portal.
 */
export enum MachinePurpose {
    /**
     * An API client an organization member provisioned; the portal manages it.
     */
    API_ACCESS = 'API_ACCESS',

    /**
     * Authenticates a project's sync workload; its secret rotates with every deploy of the
     * project named by purposeId.
     */
    PROJECT_DEPLOY = 'PROJECT_DEPLOY',

    /**
     * Authenticates a project's runtime workload, hosting the services of the project named
     * by purposeId; its secret is minted together with the workload it is baked into.
     */
    PROJECT_RUNTIME = 'PROJECT_RUNTIME',

    /**
     * Authenticates a vm-manager node in the system zone.
     */
    NODE_AGENT = 'NODE_AGENT'
}
