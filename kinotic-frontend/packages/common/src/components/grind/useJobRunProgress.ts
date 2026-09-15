import { computed, onScopeDispose, reactive, ref, type ComputedRef, type Ref } from 'vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import {
  ExecutionStatus,
  JobRunEventType,
  type JobRun,
  type JobRunEvent,
  type TaskRecord
} from '@kinotic-ai/management-api'
import type { JobTaskNode } from './JobTaskNode'

const POLL_INTERVAL_MS = 2000
const RECORD_PAGE_SIZE = 200
const MAX_RECORD_PAGES = 25

/**
 * Loads a job run and keeps its task tree current: while the run is RUNNING it follows the
 * live event stream (falling back to polling the persistent records when no stream is
 * reachable), and once the run is terminal it settles on the records. The returned root is
 * the run's own node; its children are the top-level tasks, and percentComplete is the share
 * of the discovered tree that has completed.
 */
export function useJobRunProgress(jobRunId: string) {
  const run: Ref<JobRun | null> = ref(null)
  const root: Ref<JobTaskNode | null> = ref(null)
  const loading = ref(true)
  const error = ref<string | null>(null)
  const live = ref(false)

  const nodesByPath = new Map<string, JobTaskNode>()
  let subscription: { unsubscribe(): void } | null = null
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false

  const percentComplete: ComputedRef<number> = computed(() => {
    let discovered = 0
    let completed = 0
    const walk = (nodes: JobTaskNode[]): void => {
      for (const node of nodes) {
        discovered++
        if (node.status === ExecutionStatus.COMPLETED) {
          completed++
        }
        walk(node.children)
      }
    }
    walk(root.value?.children ?? [])
    return discovered === 0 ? 0 : Math.round((completed / discovered) * 100)
  })

  function nodeAt(taskPath: string): JobTaskNode {
    let node = nodesByPath.get(taskPath)
    if (!node) {
      const separator = taskPath.lastIndexOf('/')
      node = reactive<JobTaskNode>({
        taskPath,
        sequence: Number(taskPath.slice(separator + 1)),
        description: '',
        status: ExecutionStatus.PENDING,
        dynamicTasks: false,
        error: null,
        started: null,
        finished: null,
        progress: null,
        storedName: null,
        storedValue: null,
        children: []
      })
      nodesByPath.set(taskPath, node)
      if (separator === -1) {
        root.value = node
      } else {
        // children stay ordered by sequence however discovery interleaves
        const parent = nodeAt(taskPath.slice(0, separator))
        const index = parent.children.findIndex(child => child.sequence > node!.sequence)
        parent.children.splice(index === -1 ? parent.children.length : index, 0, node)
      }
    }
    return node
  }

  function applyRecord(record: TaskRecord): void {
    const node = nodeAt(record.taskPath)
    node.description = record.description ?? node.description
    node.status = record.status
    node.dynamicTasks = record.dynamicTasks
    node.error = record.error
    node.started = record.started
    node.finished = record.finished
    node.storedName = record.storedName ?? node.storedName
    if (record.stateValue !== null && record.stateValue !== undefined) {
      node.storedValue = record.stateValue
    }
    if (record.status !== ExecutionStatus.RUNNING) {
      node.progress = null
    }
  }

  function applyEvent(event: JobRunEvent): void {
    switch (event.type) {
      case JobRunEventType.TASK_STARTED: {
        const node = nodeAt(event.taskPath)
        node.description = event.description ?? node.description
        node.status = ExecutionStatus.RUNNING
        // records carry the durable timestamps; the local clock only bridges until the next record load
        node.started = node.started ?? Date.now()
        break
      }
      case JobRunEventType.TASK_COMPLETED: {
        const node = nodeAt(event.taskPath)
        node.status = ExecutionStatus.COMPLETED
        node.finished = node.finished ?? Date.now()
        node.progress = null
        node.storedName = event.storedName ?? node.storedName
        if (event.wireValue !== null && event.wireValue !== undefined) {
          node.storedValue = event.wireValue
        }
        break
      }
      case JobRunEventType.TASK_FAILED: {
        const node = nodeAt(event.taskPath)
        node.status = ExecutionStatus.FAILED
        node.error = event.error
        node.finished = node.finished ?? Date.now()
        break
      }
      case JobRunEventType.TASK_PROGRESS: {
        nodeAt(event.taskPath).progress = {
          percentageComplete: event.percentageComplete,
          message: event.message
        }
        break
      }
      case JobRunEventType.TASKS_DISCOVERED: {
        event.tasks.forEach(applyRecord)
        if (event.dynamic) {
          nodeAt(event.taskPath).dynamicTasks = true
        }
        break
      }
      default: {
        // exhaustiveness: fails to compile when JobRunEvent gains a member this switch
        // does not handle; at runtime an unknown event from a newer server is ignored
        const unhandled: never = event
        void unhandled
        break
      }
    }
  }

  async function loadRun(): Promise<void> {
    run.value = await Kinotic.jobMonitoring.findJobRun(jobRunId)
  }

  async function loadRecords(): Promise<void> {
    for (let pageNumber = 0; pageNumber < MAX_RECORD_PAGES; pageNumber++) {
      const page = await Kinotic.jobMonitoring.findTasks(jobRunId, Pageable.create(pageNumber, RECORD_PAGE_SIZE))
      const content = page.content ?? []
      content.forEach(applyRecord)
      if (content.length < RECORD_PAGE_SIZE) {
        break
      }
    }
  }

  function startWatching(): void {
    const nodeId = run.value?.nodeId
    if (!nodeId) {
      // a run recorded before node routing existed - stay on the records
      scheduleRefresh()
      return
    }
    live.value = true
    subscription = Kinotic.jobMonitoring.watch(nodeId, jobRunId).subscribe({
      next: applyEvent,
      // the stream is unreachable (e.g. the node is gone) - stay on the records
      error: () => {
        live.value = false
        scheduleRefresh()
      },
      // the run terminated, or was not live to begin with - settle on the durable records
      complete: () => {
        live.value = false
        void refresh()
      }
    })
  }

  async function refresh(): Promise<void> {
    if (stopped) {
      return
    }
    try {
      await loadRun()
      await loadRecords()
      error.value = null
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    }
    if (run.value?.status === ExecutionStatus.RUNNING && !live.value) {
      scheduleRefresh()
    }
  }

  function scheduleRefresh(): void {
    if (stopped) {
      return
    }
    if (pollTimer) {
      clearTimeout(pollTimer)
    }
    pollTimer = setTimeout(() => {
      pollTimer = null
      void refresh()
    }, POLL_INTERVAL_MS)
  }

  async function start(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await loadRun()
      await loadRecords()
      if (run.value?.status === ExecutionStatus.RUNNING) {
        startWatching()
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  function stop(): void {
    stopped = true
    subscription?.unsubscribe()
    subscription = null
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  onScopeDispose(stop)
  void start()

  return { run, root, percentComplete, loading, error, live, refresh }
}
