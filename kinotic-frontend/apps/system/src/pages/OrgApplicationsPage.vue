<template>
  <div class="flex flex-col">
    <PageHeader title="Applications" description="Applications owned by this organization. Open one to see it as its own users do, and more." />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :is-show-add-new="false"
      :disable-modifications="true"
      :enable-row-hover="true"
      empty-state-text="No applications"
      @update:search="tableSearch = $event"
      :selected-id="highlightedApplication?.id"
      @row-hover="hoverApplication"
      @on-row-click="openApplication"
    >
      <template #item.name="{ item, index }">
        <span class="flex min-w-0 items-center gap-2.5">
          <InitialsTile :name="item.name || item.id" :index="index" />
          <span class="truncate" v-tooltip.top="item.name">{{ item.name }}</span>
        </span>
      </template>

      <template #item.id="{ item }">
        <span class="font-mono text-sm">{{ item.id }}</span>
      </template>

      <template #item.description="{ item }">
        <!-- the right padding keeps a gap before Projects as wide as the Id column leaves before it -->
        <span class="block max-w-full truncate pr-10" v-tooltip.top="item.description || null">{{ item.description || '—' }}</span>
      </template>

      <template #item.projects="{ item }">
        <TableChip :icon="ProjectsIcon" :to="`${applicationPath(organizationId, item.id)}/projects`">
          {{ item.projects }} {{ item.projects === 1 ? 'project' : 'projects' }}
        </TableChip>
      </template>

      <template #item.running="{ item }">
        <TableChip>
          <span :class="['h-2 w-2 rounded-full', item.running > 0 ? 'bg-green-500' : 'bg-surface-400']" aria-hidden="true" />
          {{ item.running }} running
        </TableChip>
      </template>

      <template #item.updated="{ item }">
        <TimePill :date="item.updated" />
      </template>
    </CrudTable>

    <!-- The picked application opens beside the list; the arrows step through the rows the table shows -->
    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownRows.length"
                    :expand-to="selectedApplication?.id ? applicationPath(organizationId, selectedApplication.id) : undefined"
                    expand-label="Open the application" @step="stepApplication">
      <template #title>
        <span v-if="selectedApplication" class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedApplication.name || selectedApplication.id }}</span>
      </template>
      <AppOverview v-if="selectedApplication?.id" :key="selectedApplication.id" :organization-id="organizationId" :application-id="selectedApplication.id" />
    </SteppingDrawer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Message from 'primevue/message'

import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage } from '@kinotic-ai/core'
import { WorkloadStatus, type Application } from '@kinotic-ai/management-api'
import {
  CrudTable,
  InitialsTile,
  PageHeader,
  ProjectsIcon,
  TableChip,
  TimePill,
  errorMessage,
  filteredPageLoader,
  SteppingDrawer,
  useCrudTablePage,
  useSteppingDrawer,
  type CrudHeader
} from '@kinotic-ai/frontend-common'

import AppOverview from '@/pages/AppOverview.vue'
import { applicationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

const props = defineProps<{
  organizationId: string
}>()

/** How many of the organization's projects the project counts consider. */
const PROJECT_PAGE_SIZE = 200

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true, width: '20%' },
  { field: 'id', header: 'Id', sortable: false, optional: true, width: '17%' },
  { field: 'description', header: 'Description', sortable: false, optional: true, width: '27%' },
  { field: 'projects', header: 'Projects', sortable: false, optional: true, width: '12%' },
  { field: 'running', header: 'Running', sortable: false, optional: true, width: '12%' },
  { field: 'updated', header: 'Updated', sortable: false, width: '12%' }
]

// Per-application counts, read once for the organization and shared by every page of the table
const projectsByApplication = ref<Record<string, number>>({})
const runningByApplication = ref<Record<string, number>>({})
const error = ref<string | null>(null)

function fetchPage(pageable: Pageable): Promise<IterablePage<Application>> {
  return Kinotic.systemOrganizations.findApplications(props.organizationId, pageable)
                .then(page => new FunctionalIterablePage(pageable, page, fetchPage))
}

// findApplications has no server-side search, so filtering is client-side over the page
const { tableSearch, dataSource, refreshTable, shownRows } = useCrudTablePage(
    filteredPageLoader(
        fetchPage,
        (app: Application) => ({
          id: app.id,
          name: app.name,
          description: app.description,
          projects: projectsByApplication.value[app.id] ?? 0,
          running: runningByApplication.value[app.id] ?? 0,
          updated: app.updated
        }),
        row => [row.name ?? null, row.id, row.description ?? null]
    ))

const { selected: selectedApplication, visible: drawerVisible, position, open: openApplication, step: stepApplication,
        highlighted: highlightedApplication, hover: hoverApplication } =
    useSteppingDrawer(shownRows, row => row.id)

async function loadCounts() {
  error.value = null
  try {
    const [projects, workloads] = await Promise.all([
      Kinotic.systemOrganizations.findProjects(props.organizationId, Pageable.create(0, PROJECT_PAGE_SIZE)),
      scanWorkloads({ organizationId: props.organizationId })
    ])
    const projectCounts: Record<string, number> = {}
    for (const project of projects.content ?? []) {
      projectCounts[project.applicationId] = (projectCounts[project.applicationId] ?? 0) + 1
    }
    const runningCounts: Record<string, number> = {}
    for (const workload of workloads) {
      if (workload.applicationId && workload.status === WorkloadStatus.RUNNING) {
        runningCounts[workload.applicationId] = (runningCounts[workload.applicationId] ?? 0) + 1
      }
    }
    projectsByApplication.value = projectCounts
    runningByApplication.value = runningCounts
  } catch (err) {
    error.value = errorMessage(err, 'Failed to count the organization\'s projects and workloads')
  }
  refreshTable()
}

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, loadCounts, { immediate: true })
</script>
