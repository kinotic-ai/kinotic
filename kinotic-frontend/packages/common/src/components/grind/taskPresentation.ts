import { markRaw, type Component } from 'vue'
import { TaskViews } from '@kinotic-ai/management-api'
import WorkloadLogTaskView from '../WorkloadLogTaskView.vue'
import ProjectArtifactsTaskView from '../deploy/ProjectArtifactsTaskView.vue'
import SbomGenerationTaskView from '../deploy/SbomGenerationTaskView.vue'
import AdvisoryImportTaskView from '../vulnerability/AdvisoryImportTaskView.vue'
import SbomScanTaskView from '../vulnerability/SbomScanTaskView.vue'
import type { JobTaskNode } from './JobTaskNode'

/**
 * How the job run views render the views tasks declare: the component for each TaskViews kind.
 * Every view component takes the view's data, the task's node and the run. A kind missing here
 * renders no detail pane.
 */
export const TASK_VIEWS: Record<string, Component> = {
  [TaskViews.WORKLOAD_LOG]: markRaw(WorkloadLogTaskView),
  [TaskViews.PROJECT_ARTIFACTS]: markRaw(ProjectArtifactsTaskView),
  [TaskViews.SBOM_GENERATION]: markRaw(SbomGenerationTaskView),
  [TaskViews.ADVISORY_IMPORT]: markRaw(AdvisoryImportTaskView),
  [TaskViews.SBOM_SCAN]: markRaw(SbomScanTaskView)
}

/** The component rendering the task's current view, or null when it shows none this console renders. */
export function taskViewComponent(node: JobTaskNode): Component | null {
  return (node.view && TASK_VIEWS[node.view.kind]) ?? null
}
