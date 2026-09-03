<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center gap-3">
      <Select
        v-model="presetMs"
        :options="presets"
        optionLabel="label"
        optionValue="ms"
        size="small"
        class="w-48"
      />
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined size="small" @click="refresh" />
      <span class="text-xs text-muted-color">{{ formatDateTime(range.start) }} — {{ formatDateTime(range.end) }}</span>
    </div>

    <Tabs v-model:value="activeTab">
      <TabList>
        <Tab value="traces">Traces</Tab>
        <Tab value="metrics">Metrics</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="traces">
          <TraceSearch v-if="activeTab === 'traces'" :organization-id="organizationId" :application-id="applicationId ?? null" :range="range" />
        </TabPanel>
        <TabPanel value="metrics">
          <MetricsPanel v-if="activeTab === 'metrics'" :organization-id="organizationId" :application-id="applicationId ?? null" :range="range" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'

import MetricsPanel from './MetricsPanel.vue'
import TraceSearch from './TraceSearch.vue'
import type { TimeRange } from './TimeRange'
import { TIME_RANGE_PRESETS, rangeEndingNow } from './telemetryApi'
import { formatDateTime } from './telemetryDisplay'

/**
 * The traces and metrics of an organization's workloads over a chosen time range, narrowed to
 * one application when one is given. An organization user passes no organization and sees its
 * own; the system console names the organization it is drilled into.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId?: string | null
}>()

// Copied, since PrimeVue types its options as a mutable array
const presets = [...TIME_RANGE_PRESETS]
const presetMs = ref<number>(60 * 60_000)
const range = ref<TimeRange>(rangeEndingNow(presetMs.value))
const activeTab = ref<string>('traces')

// A new range object each time, which is what tells the views to reload
function refresh() {
  range.value = rangeEndingNow(presetMs.value)
}

watch(presetMs, refresh)
watch(() => [props.organizationId, props.applicationId], refresh)
</script>
