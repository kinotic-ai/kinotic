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
    :selected-id="highlightedRun?.id"
      @row-hover="row => hoverRun(row as RunRow)"
    @on-row-click="row => openRun(row as RunRow)"
  >
    <template #item.name="{ item }">
      <span class="block max-w-full truncate font-sans text-sm font-semibold text-surface-950 dark:text-surface-0"
            v-tooltip.top="item.description || item.name">{{ item.name }}</span>
      <span v-if="ownerHeader" class="mt-0.5 block max-w-full truncate text-xs text-muted-color" v-tooltip.top="item.owner ?? undefined">
        {{ item.owner ?? ownerFallback }}
      </span>
    </template>

    <template #item.status="{ item }">
      <Tag :value="item.status" :severity="executionStatusSeverity(item.status)" />
    </template>

    <template #item.nodeId="{ item }">
      <span v-if="item.nodeId" class="rounded-md bg-surface-100 px-1.5 py-0.5 text-xs text-surface-600 dark:bg-surface-800 dark:text-surface-300"
            v-tooltip.top="item.nodeId">{{ item.nodeId.split('-')[0] }}</span>
      <span v-else>—</span>
    </template>

    <template #item.started="{ item }">
      <TimePill :date="item.started" />
    </template>

    <template #item.duration="{ item }">
      <span class="text-xs tabular-nums text-surface-600 dark:text-surface-300">{{ formatDuration(item.started, item.finished) }}</span>
    </template>
  </CrudTable>

  <!-- The picked run opens beside the list; the arrows step through the rows the table shows -->
  <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownRows.length"
                  :expand-to="runRoute && selectedRun ? runRoute(selectedRun.id) : undefined"
                  expand-label="Open the run's page" @step="stepRun">
    <template #title>
      <template v-if="selectedRun">
        <span class="truncate text-sm font-medium text-surface-950 dark:text-surface-0" v-tooltip.bottom="selectedRun.name">{{ selectedRun.name }}</span>
        <Tag :value="selectedRun.status" :severity="executionStatusSeverity(selectedRun.status)" class="shrink-0" />
      </template>
    </template>
    <slot v-if="selectedRun" name="run" :job-run-id="selectedRun.id" />
  </SteppingDrawer>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Tag from 'primevue/tag'
import { Direction, Kinotic, FunctionalIterablePage, Order,
         type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import type { ExecutionStatus, JobRun } from '@kinotic-ai/management-api'
import CrudTable from '../CrudTable.vue'
import SteppingDrawer from '../SteppingDrawer.vue'
import { useSteppingDrawer } from '../../composables/useSteppingDrawer'
import { useCrudTablePage } from '../useCrudTablePage'
import type { CrudHeader } from '../../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../../types/DescriptiveIdentifiable'
import DatetimeUtil from '../../util/DatetimeUtil'
import TimePill from '../TimePill.vue'
import { executionStatusSeverity } from './jobRunDisplay'
import { jobRunFilter } from './jobRunScan'

/**
 * The job runs the caller may view, most recently started first, narrowed to a scope when one
 * is given: an organization's runs, one of its applications', one of its projects', or the runs
 * in one status. Under each run's name sits what the scope leaves unsaid — the organization, the
 * application, or the project — and nothing inside a project. A clicked run opens in a
 * drawer beside the list, showing the {@code run} slot (given the run's id), whose arrows step
 * through the rows on the page; given {@code runRoute}, the drawer links to the run's own page.
 * refresh() reloads the table.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
  projectId?: string
  status?: ExecutionStatus | null
  runRoute?: (jobRunId: string) => RouteLocationRaw
}>()

defineSlots<{
  run(props: { jobRunId: string }): unknown
}>()

/** One row of the table. */
interface RunRow extends DescriptiveIdentifiable {
  id: string
  name: string
  description?: string
  status: ExecutionStatus
  owner: string | null
  nodeId: string | null
  started: string | null
  finished: string | null
}

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
    ret = 'system'
  } else if (ownerHeader.value === 'Application') {
    ret = 'organization'
  } else {
    ret = '—'
  }
  return ret
})

const headers = computed<CrudHeader[]>(() => {
  const ret: CrudHeader[] = [
    { field: 'name', header: 'Name', sortable: true, width: '38%' },
    { field: 'status', header: 'Status', sortable: false, width: '14%' }
  ]
  ret.push(
    { field: 'nodeId', header: 'Ran on', sortable: false, optional: true, width: '16%' },
    { field: 'started', header: 'Started', sortable: true, optional: true, width: '18%' },
    { field: 'duration', header: 'Duration', sortable: false, width: '14%' }
  )
  return ret
})

const filter = computed(() => jobRunFilter({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId,
  status: props.status ?? null
}))

const { tableSearch, dataSource, refreshTable, shownRows: tableRows } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const runs = await Kinotic.jobMonitoring.findJobRuns(filter.value, pageable)
  // the service has no text search, so the search narrows the fetched page by name
  const page: Page<DescriptiveIdentifiable> = {
    content: (runs.content ?? []).filter(run => matches(run, searchText)).map(toRow),
    totalElements: runs.totalElements,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function matches(run: JobRun, searchText: string | null): boolean {
  const needle = searchText?.trim().toLowerCase()
  return !needle
      || run.name.toLowerCase().includes(needle)
      || (run.description ?? '').toLowerCase().includes(needle)
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
  refreshTable()
}

const shownRows = computed(() => tableRows.value as RunRow[])

const { selected: selectedRun, visible: drawerVisible, position, open: openRun, step: stepRun,
        highlighted: highlightedRun, hover: hoverRun } =
    useSteppingDrawer(shownRows, row => row.id)

watch(filter, refresh)

defineExpose({ refresh })
</script>
