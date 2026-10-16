import { markRaw, type Component } from 'vue'
import { Crosshair, FolderGit2, Globe, ListTree, Package, Server } from '@lucide/vue'
import { TaskIcons, TaskViews } from '@kinotic-ai/management-api'
import WorkloadLogTaskView from '../WorkloadLogTaskView.vue'
import ProjectArtifactsTaskView from '../deploy/ProjectArtifactsTaskView.vue'
import SbomGenerationTaskView from '../deploy/SbomGenerationTaskView.vue'
import type { JobTaskNode } from './JobTaskNode'

/**
 * How the job run views render what tasks declare: the icon for each TaskIcons key, and the
 * component for each TaskViews kind. Every view component takes the view's data, the task's node
 * and the run. A key or kind missing here renders the default icon, or no detail pane.
 */
export const TASK_ICONS: Record<string, Component> = {
  [TaskIcons.TARGET]: markRaw(Crosshair),
  [TaskIcons.GIT]: markRaw(FolderGit2),
  [TaskIcons.PACKAGE]: markRaw(Package),
  [TaskIcons.SERVER]: markRaw(Server),
  [TaskIcons.GLOBE]: markRaw(Globe),
  [TaskIcons.SBOM]: markRaw(ListTree)
}

export const TASK_VIEWS: Record<string, Component> = {
  [TaskViews.WORKLOAD_LOG]: markRaw(WorkloadLogTaskView),
  [TaskViews.PROJECT_ARTIFACTS]: markRaw(ProjectArtifactsTaskView),
  [TaskViews.SBOM_GENERATION]: markRaw(SbomGenerationTaskView)
}

/** The component rendering the task's current view, or null when it shows none this console renders. */
export function taskViewComponent(node: JobTaskNode): Component | null {
  return (node.view && TASK_VIEWS[node.view.kind]) ?? null
}
