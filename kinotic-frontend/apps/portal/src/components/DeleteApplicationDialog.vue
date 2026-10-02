<template>
  <DeleteConfirmDialog
    :visible="application !== null"
    :name="application?.name ?? ''"
    description="This permanently deletes the application and every project in it."
    :confirm-label="progress ?? 'Delete application'"
    :loading="loading"
    :deleting="deleting"
    :removes="removes"
    :kept="kept"
    :note="note"
    @update:visible="close"
    @confirm="deleteApplication"
  />
</template>

<script setup lang="ts">
import { markRaw, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { Database, LayoutGrid } from '@lucide/vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application, Project } from '@kinotic-ai/management-api'
import { createDebug, DeleteConfirmDialog, type DeleteImpactRow, ProjectsIcon,
         showErrorToast } from '@kinotic-ai/frontend-common'
import { count, findProjectTeardown, teardownRows } from '@/util/deleteImpact'

/**
 * Confirms deleting an application, listing everything the delete removes: its projects and
 * everything their deployments run, and what it keeps, such as the projects' GitHub repositories.
 * Confirming deletes each project, then the application.
 */
const props = defineProps<{
  /** The application to delete; the dialog is open while this is set. */
  application: Application | null
}>()

const emit = defineEmits<{
  (e: 'deleted', application: Application): void
  (e: 'close'): void
}>()

const debug = createDebug('delete-application-dialog')
const toast = useToast()

/** How many projects are looked up per request, for the impact and for deleting them. */
const PROJECT_PAGE_SIZE = 200

const loading = ref(false)
const deleting = ref(false)
/** What the delete is working on while it runs. */
const progress = ref<string | null>(null)
const removes = ref<DeleteImpactRow[]>([])
const kept = ref<DeleteImpactRow[]>([])
const note = ref<string | undefined>()

watch(() => props.application, loadImpact)

async function loadImpact(application: Application | null): Promise<void> {
  removes.value = []
  kept.value = []
  note.value = undefined
  if (!application) {
    return
  }
  const applicationRow: DeleteImpactRow = { icon: markRaw(LayoutGrid), label: 'The application', items: [application.id] }
  loading.value = true
  try {
    const [page, entityCount] = await Promise.all([
      Kinotic.projects.findAllForApplication(application.id, Pageable.create(0, PROJECT_PAGE_SIZE)),
      Kinotic.entityDefinitions.countForApplication(application.id).catch(() => 0)
    ])
    const projects = page.content ?? []
    const projectCount = page.totalElements ?? projects.length
    const teardowns = await Promise.all(projects.map(findProjectTeardown))
    // the dialog may have moved on to another application while the lookups ran
    if (props.application?.id !== application.id) {
      return
    }

    const rows = teardownRows(teardowns)
    removes.value = projectCount > 0
      ? [applicationRow,
         { icon: markRaw(ProjectsIcon), label: count(projectCount, 'project'), items: projects.map(project => project.name) },
         ...rows.removes]
      : [applicationRow]
    kept.value = entityCount > 0
      ? [...rows.kept, { icon: markRaw(Database), label: count(entityCount, 'entity definition') }]
      : rows.kept
    if (teardowns.some(teardown => teardown.deployed)) {
      note.value = 'Projects are removed right away; their deployments are torn down in the background.'
    }
  } catch (error) {
    debug('Failed to look up what deleting %s affects: %O', application.id, error)
    removes.value = [applicationRow]
    note.value = 'Could not look up its projects; they are still deleted with it.'
  } finally {
    loading.value = false
  }
}

async function deleteApplication(): Promise<void> {
  const application = props.application
  if (!application) {
    return
  }
  deleting.value = true
  let current: Project | null = null
  try {
    // The server only deletes an application with no projects, so its projects go first
    let projects = await nextProjects(application.id)
    while (projects.length > 0) {
      for (const project of projects) {
        current = project
        progress.value = `Deleting ${project.name}…`
        await Kinotic.projects.deleteByIdSync(project.id ?? '')
      }
      projects = await nextProjects(application.id)
    }
    current = null
    progress.value = `Deleting ${application.name}…`
    await Kinotic.applications.deleteById(application.id)
    toast.add({ severity: 'success', summary: 'Application deleted', life: 4000 })
    emit('deleted', application)
  } catch (error) {
    showErrorToast(toast, current ? `Failed to delete project ${current.name}` : 'Failed to delete application', error, { life: 8000 })
    // some projects may be gone already, so show what is left
    await loadImpact(application)
  } finally {
    deleting.value = false
    progress.value = null
  }
}

async function nextProjects(applicationId: string): Promise<Project[]> {
  const page = await Kinotic.projects.findAllForApplication(applicationId, Pageable.create(0, PROJECT_PAGE_SIZE))
  return page.content ?? []
}

function close(): void {
  if (!deleting.value) {
    emit('close')
  }
}
</script>
