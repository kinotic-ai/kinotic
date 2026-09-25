/**
 * The kinds of watched record, the first half of a {@link WatchedParent}.
 */
export enum WatchedType {
    WORKLOAD = 'WORKLOAD',
    PROJECT_DEPLOYMENT = 'PROJECT_DEPLOYMENT',
    MICROSERVICE_DEPLOYMENT = 'MICROSERVICE_DEPLOYMENT',
    UI_DEPLOYMENT = 'UI_DEPLOYMENT',
    VM_NODE = 'VM_NODE',
    JOB_RUN = 'JOB_RUN'
}
