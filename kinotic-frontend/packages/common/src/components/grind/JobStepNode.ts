import type { RunStatus, Progress } from '@kinotic-ai/management-api'

/**
 * One discovered step of a job run, positioned by its stepPath, with the children discovered
 * beneath it. The tree grows as the run reveals structure — a task can return further steps
 * at any depth — so no level is ever final while the run executes.
 */
export interface JobStepNode {
  stepPath: string
  /** The node's position within its parent: the last stepPath segment, 0 for the run's root. */
  sequence: number
  description: string
  status: RunStatus
  /** True once the step's task revealed dynamically generated child steps. */
  dynamicSteps: boolean
  error: string | null
  /** When the step started executing, as epoch milliseconds. */
  started: number | null
  /** When the step reached a terminal status, as epoch milliseconds. */
  finished: number | null
  /** Latest live progress, present only while a watched step is running. */
  progress: Progress | null
  children: JobStepNode[]
}
