<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center gap-3">
      <Select
        v-model="presetMs"
        :options="TIME_RANGE_PRESETS"
        optionLabel="label"
        optionValue="ms"
        size="small"
        class="w-48"
      />
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined size="small" @click="refresh" />
      <span class="text-xs text-muted-color">{{ formatDateFromEpoch(range.start) }} — {{ formatDateFromEpoch(range.end) }}</span>
    </div>

    <Tabs v-model:value="activeTab">
      <TabList>
        <Tab value="traces">Traces</Tab>
        <Tab value="metrics">Metrics</Tab>
      </TabList>
      <!-- Each view mounts when first opened and is kept, so flipping tabs does not refetch -->
      <TabPanels>
        <TabPanel value="traces">
          <KeepAlive>
            <TraceSearch v-if="activeTab === 'traces'" ref="traceSearch" :organization-id="organizationId" :application-id="applicationId" :range="range" :trace-route="traceRoute" />
          </KeepAlive>
        </TabPanel>
        <TabPanel value="metrics">
          <KeepAlive>
            <MetricsPanel v-if="activeTab === 'metrics'" :organization-id="organizationId" :application-id="applicationId" :range="range" @show-failed-traces="showFailedTraces" />
          </KeepAlive>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
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
 * may. A trace picked from the search opens on the page {@code traceRoute} names, or in a
 * dialog when there is none.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  traceRoute?: (traceId: string) => RouteLocationRaw
}>()

const formatDateFromEpoch = DatetimeUtil.formatDateFromEpoch

const traceSearch = ref<InstanceType<typeof TraceSearch> | null>(null)
const presetMs = ref<number>(TIME_RANGE_PRESETS[1]!.ms)
const range = ref<TimeRange>(rangeEndingNow(presetMs.value))
const activeTab = ref<string>('traces')

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
