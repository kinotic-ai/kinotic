import { onScopeDispose, reactive, ref, type Ref } from 'vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import {
  ExecutionStatus,
  ResultType,
  stepPathOf,
  type JobRun,
  type Progress,
  type Result,
  type StepRecord
} from '@kinotic-ai/management-api'
import type { JobStepNode } from './JobStepNode'

const POLL_INTERVAL_MS = 2000
const RECORD_PAGE_SIZE = 200
const MAX_RECORD_PAGES = 25

/**
 * Loads a job run and keeps its step tree current: while the run is RUNNING it follows the
 * live result stream (falling back to polling the persistent records when no stream is
 * reachable), and once the run is terminal it settles on the records. The returned root is
 * the run's own node; its children are the top-level steps.
 */
export function useJobRunProgress(jobRunId: string) {
  const run: Ref<JobRun | null> = ref(null)
  const root: Ref<JobStepNode | null> = ref(null)
  const loading = ref(true)
  const error = ref<string | null>(null)
  const live = ref(false)

  const nodesByPath = new Map<string, JobStepNode>()
  let subscription: { unsubscribe(): void } | null = null
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false

  function nodeAt(stepPath: string): JobStepNode {
    let node = nodesByPath.get(stepPath)
    if (!node) {
      const separator = stepPath.lastIndexOf('/')
      node = reactive<JobStepNode>({
        stepPath,
        sequence: Number(stepPath.slice(separator + 1)),
        description: '',
        status: ExecutionStatus.PENDING,
        dynamicSteps: false,
        error: null,
        started: null,
        finished: null,
        progress: null,
        children: []
      })
      nodesByPath.set(stepPath, node)
      if (separator === -1) {
        root.value = node
      } else {
        // children stay ordered by sequence however discovery interleaves
        const parent = nodeAt(stepPath.slice(0, separator))
        const index = parent.children.findIndex(child => child.sequence > node!.sequence)
        parent.children.splice(index === -1 ? parent.children.length : index, 0, node)
      }
    }
    return node
  }

  function applyRecord(record: StepRecord): void {
    const node = nodeAt(record.stepPath)
    node.description = record.description ?? node.description
    node.status = record.status
    node.dynamicSteps = record.dynamicSteps
    node.error = record.error
    node.started = record.started
    node.finished = record.finished
    if (record.status !== ExecutionStatus.RUNNING) {
      node.progress = null
    }
  }

  function applyResult(result: Result): void {
    const stepPath = stepPathOf(result.stepInfo)
    switch (result.resultType) {
      case ResultType.STEP_STARTED: {
        const node = nodeAt(stepPath)
        node.description = result.value as string
        node.status = ExecutionStatus.RUNNING
        // records carry the durable timestamps; the local clock only bridges until the next record load
        node.started = node.started ?? Date.now()
        break
      }
      case ResultType.STEP_COMPLETED: {
        const node = nodeAt(stepPath)
        node.status = ExecutionStatus.COMPLETED
        node.finished = node.finished ?? Date.now()
        node.progress = null
        break
      }
      case ResultType.STEP_FAILED: {
        const node = nodeAt(stepPath)
        node.status = ExecutionStatus.FAILED
        node.error = String(result.value)
        node.finished = node.finished ?? Date.now()
        break
      }
      case ResultType.PROGRESS: {
        nodeAt(stepPath).progress = result.value as Progress
        break
      }
      case ResultType.DYNAMIC_STEPS: {
        for (const record of result.value as StepRecord[]) {
          applyRecord(record)
        }
        nodeAt(stepPath).dynamicSteps = true
        break
      }
      default:
        break
    }
  }

  async function loadRun(): Promise<void> {
    run.value = await Kinotic.jobMonitoring.findJobRun(jobRunId)
  }

  async function loadRecords(): Promise<void> {
    for (let pageNumber = 0; pageNumber < MAX_RECORD_PAGES; pageNumber++) {
      const page = await Kinotic.jobMonitoring.findSteps(jobRunId, Pageable.create(pageNumber, RECORD_PAGE_SIZE))
      const content = page.content ?? []
      content.forEach(applyRecord)
      if (content.length < RECORD_PAGE_SIZE) {
        break
      }
    }
  }

  function startWatching(): void {
    live.value = true
    subscription = Kinotic.jobMonitoring.watch(jobRunId).subscribe({
      next: applyResult,
      // the stream is unreachable (e.g. the run executes on another node) - stay on the records
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

  return { run, root, loading, error, live, refresh }
}
