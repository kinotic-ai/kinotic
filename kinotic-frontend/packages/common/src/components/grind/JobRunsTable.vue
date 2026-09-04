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
    empty-state-text="No job runs"
    @update:search="tableSearch = $event"
    @on-row-click="row => emit('open', row.id)"
  >
    <template #item.name="{ item }">
      <span class="block max-w-[24rem] truncate" :title="item.description || item.name">{{ item.name }}</span>
    </template>

    <template #item.status="{ item }">
      <Tag :value="item.status" :severity="executionStatusSeverity(item.status)" />
    </template>

    <template #item.owner="{ item }">
      <span v-if="item.owner" class="font-mono text-sm">{{ item.owner }}</span>
      <span v-else class="text-muted-color">{{ ownerFallback }}</span>
    </template>

    <template #item.nodeId="{ item }">
      <span v-if="item.nodeId" class="font-mono text-xs">{{ item.nodeId }}</span>
      <span v-else>—</span>
    </template>

    <template #item.started="{ item }">
      {{ formatEpochDateTime(item.started) }}
    </template>

    <template #item.duration="{ item }">
      {{ formatDuration(item.started, item.finished) }}
    </template>
  </CrudTable>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import Tag from 'primevue/tag'
import { Direction, Kinotic, FunctionalIterablePage, Order,
         type IterablePage, type Page, type Pageable, type Sort } from '@kinotic-ai/core'
import type { ExecutionStatus, JobRun } from '@kinotic-ai/management-api'
import CrudTable from '../CrudTable.vue'
import { pageNumberOf, useCrudTablePage } from '../useCrudTablePage'
import type { CrudHeader } from '../../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../../types/DescriptiveIdentifiable'
import DatetimeUtil from '../../util/DatetimeUtil'
import { executionStatusSeverity } from './jobRunDisplay'
import { scanJobRuns, type JobRunFilter } from './jobRunScan'

/**
 * The job runs the caller may view, most recently started first, narrowed to a scope when one
 * is given: an organization's runs ({@code organizationId}, null for the runs with none), one
 * of its applications', one of its projects', or the runs in one status. The owner column
 * names what the scope leaves unsaid — the organization, the application, or the project —
 * and disappears inside a project. Emits open with the run id when a row is clicked;
 * refresh() reloads the table.
 */
const props = defineProps<{
  organizationId?: string | null
  applicationId?: string
  projectId?: string
  status?: ExecutionStatus | null
}>()

const emit = defineEmits<{
  (e: 'open', jobRunId: string): void
}>()

/** One row of the table. */
interface RunRow extends DescriptiveIdentifiable {
  id: string
  name: string
  description?: string
  status: ExecutionStatus
  owner: string | null
  nodeId: string | null
  started: number | null
  finished: number | null
}

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatDuration = DatetimeUtil.formatDuration

const DEFAULT_SORT = [new Order('started', Direction.DESC)]

const ownerHeader = computed<string | null>(() => {
  let ret: string | null
  if (props.projectId !== undefined) {
    ret = null
  } else if (props.applicationId !== undefined) {
    ret = 'Project'
  } else if (props.organizationId !== undefined) {
    ret = 'Application'
  } else {
    ret = 'Organization'
  }
  return ret
})

const ownerFallback = computed(() => {
  let ret: string
  if (ownerHeader.value === 'Organization') {
    ret = 'platform'
  } else if (ownerHeader.value === 'Application') {
    ret = 'organization'
  } else {
    ret = '—'
  }
  return ret
})

const headers = computed<CrudHeader[]>(() => {
  const ret: CrudHeader[] = [
    { field: 'name', header: 'Name', sortable: true },
    { field: 'status', header: 'Status', sortable: false }
  ]
  if (ownerHeader.value) {
    ret.push({ field: 'owner', header: ownerHeader.value, sortable: false, optional: true })
  }
  ret.push(
    { field: 'nodeId', header: 'Ran on', sortable: false, optional: true },
    { field: 'started', header: 'Started', sortable: true, optional: true },
    { field: 'duration', header: 'Duration', sortable: false, width: '8rem' }
  )
  return ret
})

// Null when nothing narrows the runs, in which case the facade pages them itself
const filter = computed<JobRunFilter | null>(() => {
  const ret: JobRunFilter = {}
  if (props.organizationId !== undefined) ret.organizationId = props.organizationId
  if (props.applicationId !== undefined) ret.applicationId = props.applicationId
  if (props.projectId !== undefined) ret.projectId = props.projectId
  if (props.status) ret.status = props.status
  return Object.keys(ret).length > 0 ? ret : null
})

const { tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

// The scan behind a narrowed table, kept across its pages; page 0 and refresh() read anew
let scanned: JobRun[] | null = null

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  let page: Page<DescriptiveIdentifiable>
  if (filter.value) {
    if (scanned === null || pageNumberOf(pageable) === 0) {
      scanned = await scanJobRuns(filter.value)
    }
    const matching = scanned.filter(run => matches(run, searchText))
    sortRuns(matching, pageable.sort)
    const start = pageNumberOf(pageable) * pageable.pageSize
    page = {
      content: matching.slice(start, start + pageable.pageSize).map(toRow),
      totalElements: matching.length,
      cursor: undefined
    }
  } else {
    const runs = await Kinotic.jobMonitoring.findJobRuns(pageable)
    // the facade has no server-side search, so the filter narrows the fetched page by name
    page = {
      content: (runs.content ?? []).filter(run => matches(run, searchText)).map(toRow),
      totalElements: runs.totalElements,
      cursor: undefined
    }
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function matches(run: JobRun, searchText: string | null): boolean {
  const needle = searchText?.trim().toLowerCase()
  return !needle
      || run.name.toLowerCase().includes(needle)
      || (run.description ?? '').toLowerCase().includes(needle)
}

// A scanned table sorts in memory by the column the user picked; started descending otherwise
function sortRuns(runs: JobRun[], sort: Sort | null | undefined): void {
  const order = sort?.orders?.[0]
  const byName = order?.property === 'name'
  const ascending = order?.direction === Direction.ASC
  runs.sort((a, b) => {
    const cmp = byName
        ? a.name.localeCompare(b.name)
        : (a.started ?? 0) - (b.started ?? 0)
    return ascending ? cmp : -cmp
  })
}

function ownerOf(run: JobRun): string | null {
  let ret: string | null
  if (ownerHeader.value === 'Organization') {
    ret = run.organizationId ? `${run.organizationId}${run.applicationId ? ` / ${run.applicationId}` : ''}` : null
  } else if (ownerHeader.value === 'Application') {
    ret = run.applicationId ? `${run.applicationId}${run.projectId ? ` / ${run.projectId}` : ''}` : null
  } else {
    ret = run.projectId
  }
  return ret
}

function toRow(run: JobRun): RunRow {
  return {
    id: run.id ?? '',
    name: run.name,
    description: run.description ?? undefined,
    status: run.status,
    owner: ownerOf(run),
    nodeId: run.nodeId,
    started: run.started,
    finished: run.finished
  }
}

function refresh() {
  scanned = null
  refreshTable()
}

watch(filter, refresh)

defineExpose({ refresh })
</script>
