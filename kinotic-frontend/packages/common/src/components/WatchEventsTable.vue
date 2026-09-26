<template>
  <div v-if="entries.length === 0" class="text-sm text-muted-color">{{ emptyText }}</div>
  <DataTable v-else :value="entries" size="small" class="text-sm">
    <Column header="When" style="width: 11rem">
      <template #body="{ data }">
        <span :title="formatEpochDateTime(data['@timestamp'])">{{ formatRelativeDate(data['@timestamp']) }}</span>
      </template>
    </Column>
    <Column v-if="showRecord" header="Record" class="hidden md:table-cell" style="width: 18rem">
      <template #body="{ data }">
        <span class="block text-xs text-muted-color">{{ recordKind(data.type) }}</span>
        <span class="block max-w-[16rem] truncate font-mono text-xs" :title="data.id">{{ data.id }}</span>
      </template>
    </Column>
    <Column header="What happened">
      <template #body="{ data }">
        <Tag :value="kindLabel(data.kind)" :severity="kindSeverity(data.kind)" class="mr-2" />
        <span :title="valueOf(data)">{{ data.message }}</span>
      </template>
    </Column>
    <Column header="Source" class="hidden md:table-cell" style="width: 14rem">
      <template #body="{ data }"><span class="font-mono text-xs">{{ data.source }}</span></template>
    </Column>
  </DataTable>
</template>

<script setup lang="ts">
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Tag from 'primevue/tag'
import { WatchEventKind, type WatchEvent, type WatchedType } from '@kinotic-ai/management-api'
import DatetimeUtil from '../util/DatetimeUtil'

/**
 * A record's ledger entries, newest first: when each write landed, what it did, why, and what caused
 * it. A listing that mixes records, a project's deployment history, names the record on each row.
 */
withDefaults(defineProps<{
  entries: WatchEvent[]
  /** Whether each row names the record it belongs to, for a listing across records. */
  showRecord?: boolean
  emptyText?: string
}>(), {
  showRecord: false,
  emptyText: 'Nothing has happened to it yet.'
})

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatRelativeDate = DatetimeUtil.formatRelativeDate

/** Tag severity by what the entry records: a mark set is a warning, a mark cleared or an intent good news. */
const SEVERITY_BY_KIND: Record<string, string> = {
  [WatchEventKind.CONDITION_SET]: 'warn',
  [WatchEventKind.CONDITION_CLEARED]: 'success',
  [WatchEventKind.DESIRED_UPDATED]: 'info',
  [WatchEventKind.DESIRED_RENEWED]: 'info',
  [WatchEventKind.DELETION_REQUESTED]: 'danger'
}

function kindSeverity(kind: WatchEventKind): string {
  return SEVERITY_BY_KIND[kind] ?? 'secondary'
}

/** The kind as words: CONDITION_SET reads "condition set". */
function kindLabel(kind: WatchEventKind): string {
  return kind.toLowerCase().replace(/_/g, ' ')
}

function recordKind(type: WatchedType): string {
  return type.toLowerCase().replace(/_/g, ' ')
}

/** What was written, as the row's tooltip. */
function valueOf(entry: WatchEvent): string | undefined {
  return entry.value === null || entry.value === undefined ? undefined : JSON.stringify(entry.value)
}
</script>
