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
      @on-row-click="openApplication"
    >
      <template #item.id="{ item }">
        <span class="font-mono text-sm">{{ item.id }}</span>
      </template>

      <template #item.description="{ item }">
        <span class="block max-w-[22rem] truncate" :title="item.description">{{ item.description || '—' }}</span>
      </template>

      <template #item.updated="{ item }">
        {{ item.updated ? formatDate(item.updated) : '—' }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Message from 'primevue/message'

import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage } from '@kinotic-ai/core'
import { WorkloadStatus, type Application } from '@kinotic-ai/management-api'
import {
  CrudTable,
  PageHeader,
  DatetimeUtil,
  errorMessage,
  filteredPageLoader,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import { applicationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

const props = defineProps<{
  organizationId: string
}>()

/** How many of the organization's projects the project counts consider. */
const PROJECT_PAGE_SIZE = 200

const router = useRouter()
const formatDate = DatetimeUtil.formatEpochDate

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'id', header: 'Id', sortable: false, optional: true },
  { field: 'description', header: 'Description', sortable: false, optional: true },
  { field: 'projects', header: 'Projects', sortable: false, optional: true },
  { field: 'running', header: 'Running', sortable: false, optional: true },
  { field: 'updated', header: 'Updated', sortable: false }
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
const { tableSearch, dataSource, refreshTable } = useCrudTablePage(
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

function openApplication(row: DescriptiveIdentifiable) {
  router.push(applicationPath(props.organizationId, row.id ?? ''))
}

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
