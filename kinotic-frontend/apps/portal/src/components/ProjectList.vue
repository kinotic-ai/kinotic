<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import { useToast } from 'primevue/usetoast'
import CrudTable from '@/components/CrudTable.vue'
import NewProjectSidebar from '@/components/NewProjectSidebar.vue'
import ProjectEntityDefinitionsTable from '@/components/ProjectEntityDefinitionsTable.vue'
import type { IDataSource, Identifiable, IterablePage, Pageable } from '@kinotic-ai/core'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { Kinotic } from '@kinotic-ai/core'
import { Project, RepositoryConnectionStatus } from '@kinotic-ai/os-api'
import type { CrudHeader } from '@/types/CrudHeader'
import DatetimeUtil from "@/util/DatetimeUtil"
import { createDebug } from '@kinotic-ai/frontend-common'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const debug = createDebug('project-list');

const props = defineProps<{
  applicationId: string
}>()

const route = useRoute()
const router = useRouter()
const toast = useToast()

const crudTable = ref<InstanceType<typeof CrudTable>>()

const searchText = ref<string>('')
const showProjectSidebar = ref(false)
const selectedProjectId = ref<string | null>(null)
const isInitialized = ref(false)

const projectTableHeaders: CrudHeader[] = [
  { field: 'name', header: 'Project Name', sortable: true },
  { field: 'repoConnectionStatus', header: 'Repository', sortable: false },
  { field: 'sourceOfTruth', header: 'Source of Truth', sortable: true },
  { field: 'description', header: 'Description', sortable: false },
  { field: 'created', header: 'Created', sortable: false },
  { field: 'updated', header: 'Updated', sortable: false }
]

// Ids of projects whose repository initialization is currently being retried,
// to drive the per-row Retry button's loading state.
const retryingIds = ref<string[]>([])

const RepoStatus = RepositoryConnectionStatus

onMounted(() => {
  searchText.value = (route.query['search-project'] as string) || ''
  isInitialized.value = true
  handleOpenNewProjectQuery()
})

/**
 * Honors the post-install handoff from `GitHubInstallCallback`. When the user
 * started a GitHub link from the new-project sidebar, the callback redirects
 * back here with `?openNewProject=1` so we re-open the sidebar automatically.
 */
function handleOpenNewProjectQuery(): void {
  if (route.query.openNewProject === '1') {
    showProjectSidebar.value = true
    const cleaned = { ...route.query }
    delete cleaned.openNewProject
    router.replace({ query: cleaned }).catch(() => {})
  }
}

watch(() => route.query['search-project'] as string, (newVal) => {
  if (isInitialized.value) {
    searchText.value = newVal || ''
    refreshTable()
  }
})

watch(() => props.applicationId, () => {
  refreshTable()
})

const dataSource = computed<IDataSource<Project>>(() => ({
  findAll: async (pageable: Pageable): Promise<IterablePage<Project>> => {
    const result = await Kinotic.projects.findAllForApplication(props.applicationId, pageable)
    APPLICATION_STATE.projectsCount = result.totalElements ?? 0
    return result
  },
  search: async (_searchText: string, pageable: Pageable): Promise<IterablePage<Project>> => {
    const search = `applicationId:${props.applicationId} && ${searchText.value}`
    return Kinotic.projects.search(search, pageable)
  }
}))

const isDark = darkMode

function refreshTable(): void {
  crudTable.value?.find?.()
}

function updateRouteQuery(newSearch: string) {
  searchText.value = newSearch
  const newQuery = { ...route.query }

  if (newSearch) {
    newQuery['search-project'] = newSearch
  } else {
    delete newQuery['search-project']
  }

  router.replace({ query: newQuery }).catch(() => {})
  refreshTable()
}

function onAddProject(): void {
  showProjectSidebar.value = true
}

function onProjectSidebarClose(): void {
  showProjectSidebar.value = false
}

async function onProjectSubmit(): Promise<void> {
  try {
    refreshTable()
  } catch (error) {
    debug('Refresh after project creation failed: %O', error)
    showErrorToast(toast, 'Failed to refresh project list', error)
  } finally {
    showProjectSidebar.value = false
  }
}

async function deleteProject(item: Project): Promise<void> {
  try {
    await Kinotic.projects.deleteById(item.id!)
    toast.add({ severity: 'success', summary: 'Project deleted', life: 4000 })
    refreshTable()
  } catch (err) {
    showErrorToast(toast, 'Failed to delete project', err, { life: 8000 })
  }
}

async function toProjectPage(item: Identifiable<string>): Promise<void> {
  if (!item.id) return

  try {
    const appId = props.applicationId
    const projectId = item.id

    debug('Navigating to project: %s, ID: %s, App ID: %s', (item as any).name, projectId, appId)

    await router.push(`/application/${encodeURIComponent(appId)}/project/${encodeURIComponent(projectId)}/entity-definitions`)
  } catch (error) {
    debug('Failed to navigate to project page: %O', error)
  }
}

function clearSelectedProject() {
  selectedProjectId.value = null
}

function isRetrying(id: string | null): boolean {
  return id != null && retryingIds.value.includes(id)
}

/**
 * Re-runs repository initialization for a project left INITIALIZATION_FAILED
 * at create time, then refreshes the list so the updated status shows.
 */
async function retryRepoInit(project: Project): Promise<void> {
  if (!project.id) return
  retryingIds.value = [...retryingIds.value, project.id]
  try {
    await Kinotic.projects.retryRepoInitialization(project.id)
    toast.add({
      severity: 'success',
      summary: 'Repository initialized',
      detail: `Initialization succeeded for ${project.name}.`,
      life: 3000
    })
    refreshTable()
  } catch (error) {
    debug('Retry repo initialization failed for %s: %O', project.id, error)
    showErrorToast(toast, 'Retry failed', error, { fallback: `Repository initialization failed again for ${project.name}.` })
  } finally {
    retryingIds.value = retryingIds.value.filter(id => id !== project.id)
  }
}
</script>

<template>
  <div class="flex flex-1 flex-col">
    <CrudTable
      v-if="!selectedProjectId"
      ref="crudTable"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="projectTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddProject"
      @delete-item="deleteProject"
      @onRowClick="toProjectPage"
      createNewButtonText="New Project"
      emptyStateText="No projects yet"
      :isShowDelete="true"
      :isShowAddNew="true"
      class="!text-sm"
    >
      <template #item.id="{ item }">
        <span>{{ item.id }}</span>
      </template>
      <template #item.repoConnectionStatus="{ item }">
        <div
          v-if="item.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED"
          class="flex items-center gap-2"
        >
          <Tag value="Init failed" severity="warn" />
          <Button
            label="Retry"
            size="small"
            :loading="isRetrying(item.id)"
            @click.stop="retryRepoInit(item)"
          />
        </div>
        <Tag
          v-else-if="item.repoConnectionStatus === RepoStatus.DISCONNECTED"
          value="Disconnected"
          severity="danger"
        />
      </template>
      <template #item.updated="{ item }">
        <span>
          {{ DatetimeUtil.formatRelativeDate(item.updated) }}
        </span>
      </template>
      <template #item.created="{ item }">
        <span>
          {{ DatetimeUtil.formatMonthDayYear(item.created) }}
        </span>
      </template>
    </CrudTable>

    <div v-if="selectedProjectId" class="mt-6">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-semibold" :class="isDark ? 'text-surface-0' : 'text-surface-950'">
          Entities for Project: {{ selectedProjectId }}
        </h2>
        <Button label="Back to Projects" icon="pi pi-arrow-left" @click="clearSelectedProject" />
      </div>
      <ProjectEntityDefinitionsTable :projectId="selectedProjectId" />
    </div>

    <NewProjectSidebar
      :visible="showProjectSidebar"
      @close="onProjectSidebarClose"
      @submit="onProjectSubmit"
    />
  </div>
</template>
