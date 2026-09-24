<template>
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
      <RouterLink :to="applicationRoute(item.applicationId)" class="font-mono text-sm hover:underline" @click.stop>
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
        <RouterLink v-if="item.lastRunSha" :to="runRoute(item.lastRun)" class="ml-1.5 font-mono text-xs hover:underline" @click.stop>
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
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRouter, type RouteLocationRaw } from 'vue-router'
import Tag from 'primevue/tag'

import { Direction, FunctionalIterablePage, Order, type IterablePage, type Page, type Pageable, type Sort } from '@kinotic-ai/core'
import { RepositoryConnectionStatus, type JobRun, type Project } from '@kinotic-ai/management-api'

import CrudTable from '../CrudTable.vue'
import { pageNumberOf, useCrudTablePage } from '../useCrudTablePage'
import { commitShaOf, deployRunsByProject } from '../grind/deployRuns'
import { executionStatusSeverity } from '../grind/jobRunDisplay'
import type { CrudHeader } from '../../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../../types/DescriptiveIdentifiable'
import DatetimeUtil from '../../util/DatetimeUtil'
import { shortSha } from '../../util/helpers'

/**
 * The given projects, each with the state of its repository and of its last deploy run among
 * the given runs, searched, sorted and paged in memory. An application column names each
 * project's application unless every project belongs to one, as {@code applicationId} says.
 * A row opens the project.
 */
const props = defineProps<{
  projects: Project[]
  /** The runs the projects' last deploy runs are found among, newest first. */
  runs: JobRun[]
  /** The application every project belongs to; unset lists projects across applications. */
  applicationId?: string
  projectRoute: (project: Project) => RouteLocationRaw
  applicationRoute: (applicationId: string) => RouteLocationRaw
  runRoute: (run: JobRun) => RouteLocationRaw
}>()

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

const rows = computed<ProjectRow[]>(() => {
  const runsByProject = deployRunsByProject(props.runs)
  return props.projects.map(project => {
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
})

const { tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

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
    const cmp = byUpdated
        ? (DatetimeUtil.toEpochMillis(a.updated) ?? 0) - (DatetimeUtil.toEpochMillis(b.updated) ?? 0)
        : a.name.localeCompare(b.name)
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

function openProject(row: DescriptiveIdentifiable) {
  const project = props.projects.find(candidate => candidate.id === row.id)
  if (project) {
    router.push(props.projectRoute(project))
  }
}

watch(rows, () => refreshTable())
</script>
