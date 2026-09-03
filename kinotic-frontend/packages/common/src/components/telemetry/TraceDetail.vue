<template>
  <div class="flex flex-col gap-3">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <div v-else-if="loading" class="py-6 text-center text-sm text-muted-color">Loading trace…</div>
    <div v-else-if="spans.length === 0" class="py-6 text-center text-sm text-muted-color">The trace holds no spans</div>
    <template v-else>
      <div class="flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-color">
        <span>Trace <span class="font-mono">{{ traceId }}</span></span>
        <span>{{ spans.length }} spans</span>
        <span>{{ formatDuration(traceDurationMs) }}</span>
        <span>{{ formatDateTime(traceStartMs) }}</span>
        <span v-if="errorCount > 0" class="text-red-500">{{ errorCount }} failed</span>
      </div>

      <!-- The waterfall: one row per span, indented by depth, its bar placed on the trace's timeline -->
      <div class="overflow-x-auto rounded-md border border-surface">
        <div
          v-for="span in spans"
          :key="span.spanId"
          class="flex cursor-pointer items-center gap-2 border-b border-surface px-2 py-1 text-xs last:border-b-0 hover:bg-emphasis"
          :class="{ 'bg-emphasis': span.spanId === selected?.spanId }"
          @click="selected = span"
        >
          <div class="flex w-[40%] min-w-64 items-center gap-2 truncate" :style="{ paddingLeft: `${span.depth * 14}px` }">
            <span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ background: serviceColor(span.service, isDark) }" />
            <span class="truncate" :class="{ 'text-red-500': span.error }">{{ span.name }}</span>
            <span class="shrink-0 text-muted-color">{{ span.service }}</span>
          </div>
          <div class="relative h-4 flex-1">
            <div
              class="absolute top-0.5 h-3 rounded-sm"
              :class="{ 'ring-1 ring-red-500': span.error }"
              :style="barStyle(span)"
            />
          </div>
          <span class="w-20 shrink-0 text-right font-mono text-muted-color">{{ formatDuration(span.durationMs) }}</span>
        </div>
      </div>

      <div v-if="selected" class="grid gap-4 rounded-md border border-surface p-3 text-xs lg:grid-cols-2">
        <div>
          <h4 class="mb-1 text-sm font-semibold">{{ selected.name }}</h4>
          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-0.5">
            <dt class="text-muted-color">Service</dt>
            <dd>{{ selected.service }}</dd>
            <dt class="text-muted-color">Kind</dt>
            <dd>{{ selected.kind }}</dd>
            <dt class="text-muted-color">Started</dt>
            <dd class="font-mono">+{{ formatDuration(selected.startMs - traceStartMs) }}</dd>
            <dt class="text-muted-color">Duration</dt>
            <dd class="font-mono">{{ formatDuration(selected.durationMs) }}</dd>
            <dt class="text-muted-color">Status</dt>
            <dd :class="{ 'text-red-500': selected.error }">{{ selected.error ? 'error' : 'ok' }} {{ selected.statusMessage }}</dd>
            <dt class="text-muted-color">Span</dt>
            <dd class="font-mono">{{ selected.spanId }}</dd>
          </dl>
          <template v-if="selected.events.length > 0">
            <h4 class="mb-1 mt-3 text-sm font-semibold">Events</h4>
            <div v-for="(event, index) in selected.events" :key="index" class="mb-1">
              <span class="font-mono text-muted-color">+{{ formatDuration(event.timeMs - traceStartMs) }}</span>
              <span class="ml-2">{{ event.name }}</span>
              <dl class="ml-4 grid grid-cols-[auto_1fr] gap-x-4">
                <template v-for="(value, key) in event.attributes" :key="key">
                  <dt class="text-muted-color">{{ key }}</dt>
                  <dd class="break-all font-mono">{{ value }}</dd>
                </template>
              </dl>
            </div>
          </template>
        </div>
        <div>
          <h4 class="mb-1 text-sm font-semibold">Attributes</h4>
          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-0.5">
            <template v-for="(value, key) in selected.attributes" :key="key">
              <dt class="text-muted-color">{{ key }}</dt>
              <dd class="break-all font-mono">{{ value }}</dd>
            </template>
          </dl>
          <h4 class="mb-1 mt-3 text-sm font-semibold">Resource</h4>
          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-0.5">
            <template v-for="(value, key) in selected.resource" :key="key">
              <dt class="text-muted-color">{{ key }}</dt>
              <dd class="break-all font-mono">{{ value }}</dd>
            </template>
          </dl>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Message from 'primevue/message'

import { isDark } from '../../composables/useTheme'
import type { TraceSpan } from './TraceSpan'
import { fetchTrace } from './telemetryApi'
import { formatDateTime, formatDuration, serviceColor } from './telemetryDisplay'

/**
 * One trace as a waterfall of its spans, each placed on the trace's timeline and colored by the
 * service that emitted it, with the attributes of whichever span is selected.
 */
const props = defineProps<{
  organizationId: string | null
  traceId: string
}>()

const spans = ref<TraceSpan[]>([])
const selected = ref<TraceSpan | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const traceStartMs = computed(() => Math.min(...spans.value.map(span => span.startMs)))
const traceDurationMs = computed(() => Math.max(1, ...spans.value.map(span => span.startMs + span.durationMs - traceStartMs.value)))
const errorCount = computed(() => spans.value.filter(span => span.error).length)

// A span's bar spans its share of the trace, never thinner than a sliver so it stays visible
function barStyle(span: TraceSpan) {
  const left = ((span.startMs - traceStartMs.value) / traceDurationMs.value) * 100
  const width = Math.max(0.3, (span.durationMs / traceDurationMs.value) * 100)
  return {
    left: `${Math.min(left, 99.7)}%`,
    width: `${Math.min(width, 100 - left)}%`,
    background: serviceColor(span.service, isDark.value)
  }
}

async function load() {
  loading.value = true
  error.value = null
  selected.value = null
  try {
    spans.value = await fetchTrace(props.organizationId, props.traceId)
    selected.value = spans.value[0] ?? null
  } catch (err) {
    spans.value = []
    error.value = err instanceof Error ? err.message : 'Failed to load the trace'
  } finally {
    loading.value = false
  }
}

watch(() => [props.organizationId, props.traceId], load, { immediate: true })
</script>
