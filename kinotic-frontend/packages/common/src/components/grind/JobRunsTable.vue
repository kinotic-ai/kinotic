<template>
  <CrudTable
    ref="crudTable"
    :headers="headers"
    :data-source="dataSource"
    :search="tableSearch"
    :is-show-add-new="false"
    :disable-modifications="true"
    empty-state-text="No job runs"
    @update:search="tableSearch = $event"
    @on-row-click="row => emit('open', row.id)"
  >
    <template #item.status="{ item }">
      <Tag :value="item.status" :severity="executionStatusSeverity(item.status)" />
    </template>

    <template #item.organizationId="{ item }">
      <span v-if="item.organizationId" class="font-mono text-sm">{{ item.organizationId }}</span>
      <span v-else>platform</span>
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
import Tag from 'primevue/tag'
import { Kinotic, FunctionalIterablePage, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import type { JobRun } from '@kinotic-ai/os-api'
import CrudTable from '../CrudTable.vue'
import { useCrudTablePage } from '../useCrudTablePage'
import type { CrudHeader } from '../../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../../types/DescriptiveIdentifiable'
import DatetimeUtil from '../../util/DatetimeUtil'
import { executionStatusSeverity, formatDuration } from './jobRunDisplay'

/**
 * The job runs the caller may view, newest knowledge first as the facade returns them.
 * Emits open with the run id when a row is clicked; refresh() reloads the table.
 */
const emit = defineEmits<{
  (e: 'open', jobRunId: string): void
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'status', header: 'Status', sortable: false },
  { field: 'organizationId', header: 'Organization', sortable: false },
  { field: 'started', header: 'Started', sortable: true },
  { field: 'duration', header: 'Duration', sortable: false, width: '8rem' }
]

const { crudTable, tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const runs = await Kinotic.jobMonitoring.findJobRuns(pageable)
  // the facade has no server-side search, so the filter narrows the fetched page by name
  const matching = (runs.content ?? []).filter(run => matches(run, searchText))
  const page: Page<DescriptiveIdentifiable> = {
    content: matching.map(toRow),
    totalElements: runs.totalElements,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function matches(run: JobRun, searchText: string | null): boolean {
  return !searchText || run.name.toLowerCase().includes(searchText.toLowerCase())
}

function toRow(run: JobRun): DescriptiveIdentifiable {
  return {
    id: run.id ?? '',
    name: run.name,
    status: run.status,
    organizationId: run.organizationId,
    started: run.started,
    finished: run.finished
  }
}

defineExpose({ refresh: refreshTable })
</script>
