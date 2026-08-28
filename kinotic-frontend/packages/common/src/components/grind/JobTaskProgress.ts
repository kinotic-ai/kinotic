/**
 * The latest progress a running task reported: how far along it says it is, and what it says
 * it is doing.
 */
export interface JobTaskProgress {
  /** How close the task is to completion, 0 to 100. */
  percentageComplete: number
  message: string | null
}
