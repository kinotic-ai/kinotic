import type { TaskCompletedEvent } from '@/api/model/grind/events/TaskCompletedEvent'
import type { TaskFailedEvent } from '@/api/model/grind/events/TaskFailedEvent'
import type { TaskProgressEvent } from '@/api/model/grind/events/TaskProgressEvent'
import type { TaskStartedEvent } from '@/api/model/grind/events/TaskStartedEvent'
import type { TasksDiscoveredEvent } from '@/api/model/grind/events/TasksDiscoveredEvent'

/**
 * One emission from a running job's event stream: the lifecycle of each task as it happens,
 * discriminated by JobRunEventType. Watchers replay every event from the start of the run, so
 * a late subscriber sees the full history before continuing live.
 */
export type JobRunEvent =
    | TasksDiscoveredEvent
    | TaskStartedEvent
    | TaskProgressEvent
    | TaskCompletedEvent
    | TaskFailedEvent
