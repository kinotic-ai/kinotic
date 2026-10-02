<template>
  <DeleteConfirmDialog
    :visible="project !== null"
    :name="project?.name ?? ''"
    description="This permanently deletes the project and tears down its deployment."
    confirm-label="Delete project"
    :loading="loading"
    :deleting="deleting"
    :removes="removes"
    :kept="kept"
    :note="note"
    @update:visible="close"
    @confirm="deleteProject"
  />
</template>

<script setup lang="ts">
import { markRaw, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { Database } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { Project } from '@kinotic-ai/management-api'
import { DeleteConfirmDialog, type DeleteImpactRow, ProjectsIcon, showErrorToast } from '@kinotic-ai/frontend-common'
import { count, findProjectTeardown, teardownRows } from '@/util/deleteImpact'

/**
 * Confirms deleting a project, listing everything its deployment runs that the delete tears down
 * and what it keeps, such as the project's GitHub repository.
 */
const props = defineProps<{
  /** The project to delete; the dialog is open while this is set. */
  project: Project | null
}>()

const emit = defineEmits<{
  (e: 'deleted', project: Project): void
  (e: 'close'): void
}>()

const toast = useToast()

const loading = ref(false)
const deleting = ref(false)
const removes = ref<DeleteImpactRow[]>([])
const kept = ref<DeleteImpactRow[]>([])
const note = ref<string | undefined>()

watch(() => props.project, loadImpact)

async function loadImpact(project: Project | null): Promise<void> {
  removes.value = []
  kept.value = []
  note.value = undefined
  if (!project?.id) {
    return
  }
  const projectId = project.id
  loading.value = true
  const [teardown, entityCount] = await Promise.all([
    findProjectTeardown(project),
    Kinotic.entityDefinitions.countForProject(projectId).catch(() => 0)
  ])
  // the dialog may have moved on to another project while the lookups ran
  if (props.project?.id !== projectId) {
    return
  }

  const rows = teardownRows([teardown])
  removes.value = [{ icon: markRaw(ProjectsIcon), label: 'The project', items: [project.name] }, ...rows.removes]
  kept.value = entityCount > 0
    ? [...rows.kept, { icon: markRaw(Database), label: count(entityCount, 'entity definition') }]
    : rows.kept
  if (teardown.deployed) {
    note.value = 'The project is removed right away; its deployment is torn down in the background.'
  }
  loading.value = false
}

async function deleteProject(): Promise<void> {
  const project = props.project
  if (!project?.id) {
    return
  }
  deleting.value = true
  try {
    await Kinotic.projects.deleteByIdSync(project.id)
    toast.add({ severity: 'success', summary: 'Project deleted', life: 4000 })
    emit('deleted', project)
  } catch (error) {
    showErrorToast(toast, 'Failed to delete project', error, { life: 8000 })
  } finally {
    deleting.value = false
  }
}

function close(): void {
  if (!deleting.value) {
    emit('close')
  }
}
</script>
