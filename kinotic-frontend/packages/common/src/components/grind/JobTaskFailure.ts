/** How a failed task reads to the person whose run it was. */
export interface JobTaskFailure {
  /** What went wrong, in plain words. */
  explanation: string
  /** True when the failure is on the Kinotic platform's side rather than caused by the project. */
  platform: boolean
}
