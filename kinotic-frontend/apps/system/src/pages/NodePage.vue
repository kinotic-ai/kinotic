<template>
  <div class="flex flex-col">
    <PageHeader :title="node?.name ?? nodeId">
      <template #eyebrow>
        <RouterLink to="/worker-nodes" class="hover:underline">Worker nodes</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate">{{ node?.name ?? nodeId }}</span>
      </template>
      <template #actions>
        <Tag v-if="node" :value="nodeHealth(node)" :severity="nodeSeverity(nodeHealth(node))" />
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <template v-if="node">
      <Message v-if="node.state.deletionRequested" severity="info" :closable="false" class="mb-4">
        <b>Deregistering.</b> Its removal was asked for {{ formatEpochDateTime(node.state.deletionRequested) }};
        the orchestrator records any run still open on it as failed and removes the node.
      </Message>
      <Message v-if="health === NodeHealth.UNREACHABLE" severity="error" :closable="false" class="mb-4">
        <b>Unreachable.</b> {{ unreachable?.message }}, since {{ formatEpochDateTime(unreachable?.since ?? null) }}.
        Last heartbeat {{ formatEpochDateTime(node.lastSeen) }}. The orchestrator places nothing new here;
        its {{ workloads.length }} workloads keep their last reported status until the node's next
        heartbeat, which makes it reachable again, or its deregistration, which records the open ones as failed.
      </Message>
      <Message v-else-if="health === NodeHealth.DRAINING" severity="warn" :closable="false" class="mb-4">
        <b>Draining.</b> {{ node.healthMessage ?? 'The node reported a problem.' }}
        The orchestrator places nothing new here until the node reports no problems; its
        {{ workloads.length }} workloads keep running.
      </Message>
      <Message v-else-if="health === NodeHealth.UNKNOWN" severity="secondary" :closable="false" class="mb-4">
        <b>Not reported yet.</b> The node has not reported whether it takes workloads since it registered;
        its next heartbeat says.
      </Message>

      <div class="flex flex-col gap-4">
        <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
          <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                    :value="stat.value" :detail="stat.detail" :to="stat.to" />
        </div>

        <DashboardSection :icon="Boxes" :tint="TINTS.ink" title="Workloads on this node" :count="workloads.length"
                          description="Click a row for the workload; its menu shows its logs, stops, restarts or destroys it."
                          :link-to="{ path: '/workloads', query: { node: nodeId } }" link-label="Filter workloads">
          <!-- WorkloadsTable brings its own search bar and paginator, which need the card's padding -->
          <div class="p-4">
            <WorkloadsTable :workloads="workloads" :scope="{}" :show-node="false" @changed="load" />
          </div>
        </DashboardSection>

        <div class="grid gap-4 lg:grid-cols-2">
          <DashboardSection :icon="Server" :tint="TINTS.ink" title="Details">
            <div class="px-5 pb-3">
              <FactList :facts="facts" />
            </div>
          </DashboardSection>
          <DashboardSection :icon="Gauge" :tint="TINTS.ink" title="Capacity"
                            description="What the node promised at registration, less what is placed on it.">
            <div class="p-5">
              <CapacityRows :capacity="capacityOf([node])" />
            </div>
          </DashboardSection>
        </div>

        <DashboardSection :icon="History" :tint="TINTS.ink" title="History" :count="history.length"
                          :description="`What happened to the node, newest first: each change of what it should be and of what it reports, and each mark set beside them, with what caused it. The latest ${HISTORY_PAGE_SIZE} entries.`">
          <EmptyChartCharacter v-if="history.length === 0" class="py-6" title="Nothing has happened to the node yet" />
          <WatchEventsTimeline v-else :entries="history" empty-text="Nothing has happened to the node yet." />
        </DashboardSection>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Boxes, Clock, Cpu, FolderOpen, Gauge, HardDrive, Hash, History, Layers, MemoryStick, Network, Server } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus, type WatchEvent, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DashboardSection, DatetimeUtil, FactList, PageHeader, StatCard, TINTS, WatchEventsTimeline, errorMessage,
         formatMb, EmptyChartCharacter } from '@kinotic-ai/frontend-common'

import CapacityRows from '@/components/CapacityRows.vue'
import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { NodeHealth, capacityOf, formatCpus, nodeHealth, nodeSeverity, nodeUnreachable, percentOf } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

/**
 * One worker node: its health explained, its capacity, the workloads placed on it, and what it
 * reported at registration.
 */
const props = defineProps<{
  nodeId: string
}>()

const HISTORY_PAGE_SIZE = 50

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const node = ref<VmNode | null>(null)
const workloads = ref<Workload[]>([])
const history = ref<WatchEvent[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const health = computed(() => node.value ? nodeHealth(node.value) : NodeHealth.UNKNOWN)
const unreachable = computed(() => node.value ? nodeUnreachable(node.value) : undefined)

interface Stat {
  label: string
  value: string
  detail: string
  to?: string
  icon: Component
  /** One of TINTS. */
  tint: string
}

const stats = computed<Stat[]>(() => {
  const n = node.value
  if (!n) return []
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  return [
    {
      label: 'CPU',
      value: `${percentOf(n.totalCpus - n.freeCpus, n.totalCpus)}%`,
      detail: `${formatCpus(n.totalCpus - n.freeCpus)} of ${n.totalCpus} CPU allocated`,
      icon: markRaw(Cpu),
      tint: TINTS.ink
    },
    {
      label: 'Memory',
      value: `${percentOf(n.totalMemoryMb - n.freeMemoryMb, n.totalMemoryMb)}%`,
      detail: `${formatMb(n.totalMemoryMb - n.freeMemoryMb)} of ${formatMb(n.totalMemoryMb)}`,
      icon: markRaw(MemoryStick),
      tint: TINTS.ink
    },
    {
      label: 'Disk',
      value: `${percentOf(n.totalDiskMb - n.freeDiskMb, n.totalDiskMb)}%`,
      detail: `${formatMb(n.totalDiskMb - n.freeDiskMb)} of ${formatMb(n.totalDiskMb)}`,
      icon: markRaw(HardDrive),
      tint: TINTS.ink
    },
    {
      label: 'Workloads',
      value: `${running}`,
      detail: `running of ${workloads.value.length} placed here`,
      to: `/workloads?node=${encodeURIComponent(props.nodeId)}`,
      icon: markRaw(Boxes),
      tint: TINTS.ink
    }
  ]
})

const facts = computed(() => {
  const n = node.value
  return [
    { label: 'Node id', icon: markRaw(Hash), value: n?.id ?? props.nodeId, mono: true },
    { label: 'Host', icon: markRaw(Network), value: n?.hostname ?? '—', mono: true },
    { label: 'Provider', icon: markRaw(Layers), value: n?.providerType ?? '—' },
    { label: 'Data dir', icon: markRaw(FolderOpen), value: n?.workloadDataDir ?? '—', mono: true },
    { label: 'Last heartbeat', icon: markRaw(Clock), value: formatEpochDateTime(n?.lastSeen ?? null) }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const [found, placed, entries] = await Promise.all([
      Kinotic.vmNodes.findById(props.nodeId),
      scanWorkloads({}, { nodeId: props.nodeId }),
      Kinotic.vmNodes.findHistory(props.nodeId, Pageable.create(0, HISTORY_PAGE_SIZE))
    ])
    node.value = found
    workloads.value = placed
    history.value = entries.content ?? []
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the worker node')
  } finally {
    loading.value = false
  }
}

watch(() => props.nodeId, load, { immediate: true })
</script>
