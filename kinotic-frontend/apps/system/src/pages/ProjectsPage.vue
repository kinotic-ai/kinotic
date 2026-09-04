<template>
  <div class="flex flex-col">
    <PageHeader title="Projects" :description="description" />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :default-sort="DEFAULT_SORT"
      :is-show-add-new="false"
      :disable-modifications="true"
      :enable-row-hover="true"
      empty-state-text="No projects"
      @update:search="tableSearch = $event"
      @on-row-click="openProject"
    >
      <template #item.applicationId="{ item }">
        <RouterLink :to="applicationPath(organizationId, item.applicationId)" class="font-mono text-sm hover:underline" @click.stop>
          {{ item.applicationId }}
        </RouterLink>
      </template>

      <template #item.repoFullName="{ item }">
        <span class="font-mono text-xs">{{ item.repoFullName || '—' }}</span>
      </template>

      <template #item.repoStatus="{ item }">
        <Tag :value="repoStatusLabel(item.repoStatus)" :severity="repoStatusSeverity(item.repoStatus)" />
      </template>

      <template #item.lastRun="{ item }">
        <template v-if="item.lastRun">
          <Tag :value="item.lastRun.status" :severity="executionStatusSeverity(item.lastRun.status)" />
          <RouterLink v-if="item.lastRunSha" :to="runPath(item.lastRun)" class="ml-1.5 font-mono text-xs hover:underline" @click.stop>
            {{ shortSha(item.lastRunSha) }}
          </RouterLink>
        </template>
        <span v-else class="text-xs text-muted-color">Never deployed</span>
      </template>

      <template #item.description="{ item }">
        <span class="block max-w-[16rem] truncate" :title="item.description">{{ item.description || '—' }}</span>
      </template>

      <template #item.updated="{ item }">
        {{ item.updated ? formatDate(item.updated) : '—' }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { Direction, FunctionalIterablePage, Kinotic, Order, Pageable, type IterablePage, type Page, type Sort } from '@kinotic-ai/core'
import { RepositoryConnectionStatus, type JobRun, type Project } from '@kinotic-ai/management-api'
import {
  CrudTable,
  PageHeader,
  DatetimeUtil,
  errorMessage,
  executionStatusSeverity,
  pageNumberOf,
  scanJobRuns,
  shortSha,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import { commitShaOf, deployRunsByProject } from '@/util/runs'
import { applicationPath, projectPath, scopePath } from '@/util/scope'

/**
 * The projects of an organization across all of its applications, or of one application, each
 * with the state of its repository and of its last deploy run. A row opens the project.
 */
const props = defineProps<{
  organizationId: string
  applicationId?: string
}>()

/** How many of the organization's projects the page lists. */
const PROJECT_PAGE_SIZE = 200

/** One row of the table. */
interface ProjectRow extends DescriptiveIdentifiable {
  id: string
  name: string
  applicationId: string
  repoFullName: string
  repoStatus: RepositoryConnectionStatus | null
  lastRun: JobRun | null
  lastRunSha: string | null
  description?: string
  updated: number | null
}

const DEFAULT_SORT = [new Order('name', Direction.ASC)]

const router = useRouter()
const formatDate = DatetimeUtil.formatEpochDate

const description = computed(() => props.applicationId
    ? 'The functional units that make up this application, each backed by a GitHub repository, with the state of its last deploy run.'
    : 'The organization\'s projects across all of its applications, with the state of each project\'s last deploy run.')

const headers = computed<CrudHeader[]>(() => {
  const ret: CrudHeader[] = [{ field: 'name', header: 'Name', sortable: true }]
  if (!props.applicationId) {
    ret.push({ field: 'applicationId', header: 'Application', sortable: false, optional: true })
  }
  ret.push(
    { field: 'repoFullName', header: 'Repository', sortable: false, optional: true },
    { field: 'repoStatus', header: 'Repo status', sortable: false },
    { field: 'lastRun', header: 'Last run', sortable: false },
    { field: 'description', header: 'Description', sortable: false, optional: true },
    { field: 'updated', header: 'Updated', sortable: true, optional: true }
  )
  return ret
})

const rows = ref<ProjectRow[]>([])
const error = ref<string | null>(null)

const { tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

// The rows are read once for the scope and searched, sorted and paged in memory
async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const needle = searchText?.trim().toLowerCase()
  const matching = rows.value.filter(row => !needle
      || [row.name, row.applicationId, row.repoFullName, row.description].some(value => (value ?? '').toLowerCase().includes(needle)))
  sortRows(matching, pageable.sort)
  const start = pageNumberOf(pageable) * pageable.pageSize
  const page: Page<DescriptiveIdentifiable> = {
    content: matching.slice(start, start + pageable.pageSize),
    totalElements: matching.length,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function sortRows(list: ProjectRow[], sort: Sort | null | undefined): void {
  const order = sort?.orders?.[0]
  const byUpdated = order?.property === 'updated'
  const ascending = order ? order.direction === Direction.ASC : true
  list.sort((a, b) => {
    const cmp = byUpdated ? (a.updated ?? 0) - (b.updated ?? 0) : a.name.localeCompare(b.name)
    return ascending ? cmp : -cmp
  })
}

function repoStatusLabel(status: RepositoryConnectionStatus | null): string {
  let ret: string
  if (status === RepositoryConnectionStatus.INITIALIZATION_FAILED) {
    ret = 'Init failed'
  } else if (status === RepositoryConnectionStatus.DISCONNECTED) {
    ret = 'Disconnected'
  } else {
    ret = 'Connected'
  }
  return ret
}

function repoStatusSeverity(status: RepositoryConnectionStatus | null): string {
  let ret: string
  if (status === RepositoryConnectionStatus.INITIALIZATION_FAILED) {
    ret = 'warn'
  } else if (status === RepositoryConnectionStatus.DISCONNECTED) {
    ret = 'danger'
  } else {
    ret = 'success'
  }
  return ret
}

function runPath(run: JobRun): string {
  return `${scopePath({ organizationId: props.organizationId, applicationId: props.applicationId })}/jobs/${encodeURIComponent(run.id ?? '')}`
}

function openProject(row: DescriptiveIdentifiable) {
  const project = rows.value.find(candidate => candidate.id === row.id)
  if (project) {
    router.push(projectPath(props.organizationId, project.applicationId, project.id))
  }
}

async function loadRows() {
  error.value = null
  try {
    const [projects, runs] = await Promise.all([
      Kinotic.systemOrganizations.findProjects(props.organizationId, Pageable.create(0, PROJECT_PAGE_SIZE)),
      scanJobRuns({ organizationId: props.organizationId, applicationId: props.applicationId })
    ])
    const runsByProject = deployRunsByProject(runs)
    rows.value = (projects.content ?? [])
        .filter(project => !props.applicationId || project.applicationId === props.applicationId)
        .map((project: Project) => {
          const lastRun = runsByProject.get(project.id ?? '')?.[0] ?? null
          return {
            id: project.id ?? '',
            name: project.name,
            applicationId: project.applicationId,
            repoFullName: project.repoFullName,
            repoStatus: project.repoConnectionStatus,
            lastRun,
            lastRunSha: lastRun ? commitShaOf(lastRun) : null,
            description: project.description,
            updated: project.updated
          }
        })
  } catch (err) {
    rows.value = []
    error.value = errorMessage(err, 'Failed to load the projects')
  }
  refreshTable()
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], loadRows, { immediate: true })
</script>
