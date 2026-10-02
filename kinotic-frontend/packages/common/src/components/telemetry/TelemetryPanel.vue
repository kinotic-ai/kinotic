<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center gap-3">
      <Select checkmark
        v-model="presetMs"
        :options="TIME_RANGE_PRESETS"
        optionLabel="label"
        optionValue="ms"
        size="small"
        class="w-48"
      />
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined size="small" @click="refresh" />
      <div class="inline-flex items-center gap-2.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-1.5 text-[0.8125rem] dark:border-surface-700 dark:bg-surface-800/60"
           :aria-label="`From ${formatDateFromEpoch(range.start)} to ${formatDateFromEpoch(range.end)}`">
        <CalendarClock :size="15" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
        <span class="font-medium text-surface-800 dark:text-surface-100">{{ dayLabel(range.start) }}</span>
        <span class="font-mono tabular-nums text-surface-700 dark:text-surface-200">{{ formatTime(range.start) }}</span>
        <ArrowRight :size="14" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
        <span v-if="dayLabel(range.end) !== dayLabel(range.start)" class="font-medium text-surface-800 dark:text-surface-100">{{ dayLabel(range.end) }}</span>
        <span class="font-mono tabular-nums text-surface-700 dark:text-surface-200">{{ formatTime(range.end) }}</span>
      </div>
    </div>

    <Tabs v-model:value="activeTab">
      <TabList>
        <Tab value="metrics"><span class="flex items-center gap-2"><ChartLine :size="18" :stroke-width="1.75" aria-hidden="true" />Metrics</span></Tab>
        <Tab value="traces"><span class="flex items-center gap-2"><ChartGantt :size="18" :stroke-width="1.75" aria-hidden="true" />Traces</span></Tab>
      </TabList>
      <!-- Each view mounts when first opened and is kept, so flipping tabs does not refetch; the
           panels drop their side padding so the filters and the list line up with the page edge -->
      <TabPanels class="!px-0">
        <TabPanel value="metrics">
          <KeepAlive>
            <MetricsPanel v-if="activeTab === 'metrics'" :organization-id="organizationId" :application-id="applicationId" :range="range" @show-failed-traces="showFailedTraces" />
          </KeepAlive>
        </TabPanel>
        <TabPanel value="traces">
          <KeepAlive>
            <TraceSearch v-if="activeTab === 'traces'" ref="traceSearch" :organization-id="organizationId" :application-id="applicationId" :range="range" />
          </KeepAlive>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, CalendarClock, ChartGantt, ChartLine } from '@lucide/vue'
import { nextTick, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'

import DatetimeUtil from '../../util/DatetimeUtil'
import MetricsPanel from './MetricsPanel.vue'
import TraceSearch from './TraceSearch.vue'
import type { TimeRange } from './TimeRange'
import { TIME_RANGE_PRESETS, rangeEndingNow } from './telemetryApi'

/**
 * The traces and metrics of an organization's workloads over a chosen time range, narrowed to
 * one application when one is given. The organization is the one whose tenant the signed-in
 * user may read: an organization user's own, or the one the system console is drilled into;
 * null reads the system tenant, the platform's own telemetry, which only a platform operator
 * may. A trace picked from the search opens in a drawer over the results.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
}>()

const formatDateFromEpoch = DatetimeUtil.formatDateFromEpoch

/** An instant's hour and minute on the 24-hour clock: "09:59". */
function formatTime(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
}

/** The calendar day of an instant, in words for today and yesterday: "Today", "Yesterday", "Sep 30". */
function dayLabel(epochMillis: number): string {
  const day = new Date(epochMillis)
  const today = new Date()
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1)
  let ret: string
  if (day.toDateString() === today.toDateString()) {
    ret = 'Today'
  } else if (day.toDateString() === yesterday.toDateString()) {
    ret = 'Yesterday'
  } else {
    ret = day.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  }
  return ret
}

const traceSearch = ref<InstanceType<typeof TraceSearch> | null>(null)
const presetMs = ref<number>(TIME_RANGE_PRESETS[1]!.ms)
const range = ref<TimeRange>(rangeEndingNow(presetMs.value))
// Metrics open first: the overview before the individual traces behind it
const activeTab = ref<string>('metrics')

// A new range object each time, which is what tells the views to reload
function refresh() {
  range.value = rangeEndingNow(presetMs.value)
}

/** Opens the Traces view narrowed to the traces with a failed span, over the same range. */
async function showFailedTraces() {
  activeTab.value = 'traces'
  // the view mounts, or comes back from the keep-alive cache, on the next render
  await nextTick()
  traceSearch.value?.searchErrors()
}

watch(presetMs, refresh)
watch(() => [props.organizationId, props.applicationId], refresh)
</script>
