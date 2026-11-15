/**
 * How usage of a UsageMeter is measured.
 */
export enum UsageMeasurementType {
    /** A fixed amount for each hour something runs, counted from the moment it starts to the moment it stops. */
    RUNNING_RESOURCE = 'RUNNING_RESOURCE',
    /** A level read once an hour, such as the gigabytes stored, which stands for the whole hour. */
    POLLED_LEVEL = 'POLLED_LEVEL'
}
