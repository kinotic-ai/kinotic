import type { ExecutionStatus } from '@kinotic-ai/management-api'
import type { JobTaskProgress } from './JobTaskProgress'

/**
 * One discovered task of a job run, positioned by its taskPath, with the children discovered
 * beneath it. The tree grows as the run reveals structure — a task can return further tasks
 * at any depth — so no level is ever final while the run executes.
 */
export interface JobTaskNode {
  taskPath: string
  /** The node's position within its parent: the last taskPath segment, 0 for the run's root. */
  sequence: number
  description: string
  status: ExecutionStatus
  /** True once the task revealed dynamically generated child tasks. */
  dynamicTasks: boolean
  error: string | null
  /** When the task started executing, as epoch milliseconds. */
  started: number | null
  /** When the task reached a terminal status, as epoch milliseconds. */
  finished: number | null
  /** Latest live progress, present only while a watched task is running. */
  progress: JobTaskProgress | null
  children: JobTaskNode[]
}
