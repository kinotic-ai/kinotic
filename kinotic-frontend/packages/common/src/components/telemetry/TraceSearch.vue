<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-end gap-3">
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Service</label>
        <InputText v-model="filters.service" size="small" placeholder="Any service" @keyup.enter="search" />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Span name</label>
        <InputText v-model="filters.spanName" size="small" placeholder="Any span" @keyup.enter="search" />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Min duration (ms)</label>
        <InputNumber v-model="filters.minDurationMs" size="small" :min="0" :useGrouping="false" placeholder="0" inputClass="w-28" />
      </div>
      <div class="flex items-center gap-2 pb-2">
        <Checkbox v-model="filters.onlyErrors" inputId="only-errors" binary />
        <label for="only-errors" class="text-sm">Errors only</label>
      </div>
      <Button label="Search" icon="pi pi-search" size="small" :loading="loading" @click="search" />
      <span class="pb-2 font-mono text-xs text-muted-color">{{ query }}</span>
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DataTable
      :value="traces"
      dataKey="traceId"
      size="small"
      selectionMode="single"
      :class="['text-sm', { 'datatable-loading': loading }]"
      @row-select="openTrace($event.data)"
    >
      <template #empty>
        <div class="py-6 text-center text-sm text-muted-color">{{ loading ? 'Searching traces…' : 'No traces match in this range' }}</div>
      </template>
      <Column header="Started" style="width: 12rem">
        <template #body="{ data }">
          <span class="font-mono text-xs">{{ formatDateFromEpoch(data.startMs) }}</span>
        </template>
      </Column>
      <Column field="rootService" header="Service" />
      <Column field="rootName" header="Root span" />
      <Column header="Duration" style="width: 8rem">
        <template #body="{ data }">{{ formatDuration(data.durationMs) }}</template>
      </Column>
      <Column field="matchedSpans" header="Spans" style="width: 6rem" />
      <Column header="Trace" style="width: 10rem">
        <template #body="{ data }">
          <span class="font-mono text-xs text-muted-color">{{ data.traceId.slice(0, 16) }}</span>
        </template>
      </Column>
    </DataTable>

    <Dialog v-if="!traceRoute" v-model:visible="detailVisible" modal maximizable :style="{ width: '90vw' }" :header="detailHeader">
      <TraceDetail v-if="selectedTraceId" :organization-id="organizationId" :trace-id="selectedTraceId" />
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'

import '../../styles/datatable-loading.css'
import DatetimeUtil from '../../util/DatetimeUtil'
import { errorMessage } from '../../util/helpers'
import TraceDetail from './TraceDetail.vue'
import type { TimeRange } from './TimeRange'
import type { TraceSummary } from './TraceSummary'
import { searchTraces, traceQl, type TraceFilters } from './telemetryApi'
import { formatDuration } from './telemetryDisplay'

/**
 * Searches the organization's traces — or one application's — over the given range, and opens
 * the one picked from the results: on the page {@code traceRoute} names when given, otherwise
 * in a dialog over the results.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  range: TimeRange
  traceRoute?: (traceId: string) => RouteLocationRaw
}>()

const router = useRouter()

/** How many traces one search returns. */
const SEARCH_LIMIT = 50

// What the user narrows by; the application comes from the props at query time
const filters = reactive<Omit<TraceFilters, 'applicationId'>>({
  service: '',
  spanName: '',
  onlyErrors: false,
  minDurationMs: null
})

const traces = ref<TraceSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedTraceId = ref<string | null>(null)
const detailVisible = ref(false)

const query = computed(() => traceQl({ ...filters, applicationId: props.applicationId }))
const formatDateFromEpoch = DatetimeUtil.formatDateFromEpoch

const detailHeader = computed(() => {
  const selected = traces.value.find(trace => trace.traceId === selectedTraceId.value)
  return selected ? `${selected.rootService} — ${selected.rootName}` : 'Trace'
})

async function search() {
  loading.value = true
  error.value = null
  try {
    traces.value = await searchTraces(props.organizationId, query.value, props.range, SEARCH_LIMIT)
  } catch (err) {
    traces.value = []
    error.value = errorMessage(err, 'Failed to search traces')
  } finally {
    loading.value = false
  }
}

function openTrace(trace: TraceSummary) {
  if (props.traceRoute) {
    router.push(props.traceRoute(trace.traceId))
  } else {
    selectedTraceId.value = trace.traceId
    detailVisible.value = true
  }
}

// The panel replaces the range on every refresh and scope change, so it is the one trigger
watch(() => props.range, search, { immediate: true })
</script>
