/** A kind of activity the notification feed reports, each switched on or off in the settings. */
export enum NotificationCategory {
    RUN_FAILED = 'RUN_FAILED',
    RUN_COMPLETED = 'RUN_COMPLETED',
    RUN_STARTED = 'RUN_STARTED',
    DEPLOYMENT_FAILED = 'DEPLOYMENT_FAILED',
    DEPLOYMENT_SUCCEEDED = 'DEPLOYMENT_SUCCEEDED',
    DEPLOYMENT_WARNING = 'DEPLOYMENT_WARNING',
    DEPLOYMENT_REMOVED = 'DEPLOYMENT_REMOVED'
}
