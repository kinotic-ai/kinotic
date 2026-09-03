<template>
  <div>
    <PageHeader title="Dashboard" description="Cluster health and platform totals at a glance." />

    <Message v-if="clusterError" severity="error" :closable="false" class="mb-4">{{ clusterError }}</Message>

    <div class="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-5">
      <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
    </div>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      <div class="rounded-lg border border-surface p-4">
        <h2 class="text-base font-semibold">Worker capacity</h2>
        <p class="mb-4 text-xs text-muted-color">
          Allocated versus total across the worker nodes that are online.
        </p>
        <div v-if="workerNodes.length === 0" class="py-6 text-center text-sm text-muted-color">
          No worker nodes registered
        </div>
        <div v-else-if="onlineNodes.length === 0" class="py-6 text-center text-sm text-muted-color">
          None of the {{ workerNodes.length }} registered worker nodes are online
        </div>
        <div v-else class="flex flex-col gap-3">
          <div v-for="row in capacityRows" :key="row.label">
            <div class="mb-1 flex justify-between text-sm">
              <span class="text-muted-color">{{ row.label }}</span>
              <span>{{ row.text }}</span>
            </div>
            <CapacityBar :pct="row.pct" />
          </div>
        </div>
      </div>

      <WorkloadStateCard description="Every workload on the platform, by state." view-all-to="/worker-nodes" />
    </div>

    <div class="mt-6 rounded-lg border border-surface">
      <div class="px-4 pt-4 pb-2">
        <h2 class="text-base font-semibold">Server nodes</h2>
        <p class="text-xs text-muted-color">
          Every kinotic-server in the cluster. One node serves this console's connection.
        </p>
      </div>
      <div class="overflow-x-auto px-4 pb-4">
        <table class="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th class="border-b border-surface px-2 py-1.5 text-left font-medium text-muted-color">Node</th>
              <th class="border-b border-surface px-2 py-1.5 text-left font-medium text-muted-color">Version</th>
              <th class="border-b border-surface px-2 py-1.5 text-left font-medium text-muted-color">Join order</th>
              <th class="border-b border-surface px-2 py-1.5"></th>
              <th class="border-b border-surface px-2 py-1.5"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in clusterInfo?.nodes ?? []" :key="node.nodeId">
              <td class="border-b border-surface px-2 py-1.5 font-mono text-xs">{{ node.nodeId }}</td>
              <td class="border-b border-surface px-2 py-1.5">{{ node.version }}</td>
              <td class="border-b border-surface px-2 py-1.5">{{ node.order }}</td>
              <td class="border-b border-surface px-2 py-1.5">
                <Tag v-if="node.local" severity="info" value="serving request" />
              </td>
              <td class="border-b border-surface px-2 py-1.5 text-right">
                <Button label="Logging" icon="pi pi-sliders-h" severity="secondary" text size="small"
                        @click="openLogLevel(node.nodeId)" />
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!clusterError && (clusterInfo?.nodes ?? []).length === 0" class="py-6 text-center text-sm text-muted-color">
          Loading cluster topology…
        </div>
      </div>
    </div>

    <LogLevelDialog v-if="logLevelNodeId" v-model:visible="logLevelVisible" :node-id="logLevelNodeId" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { KinoticClusterInfo } from '@kinotic-ai/system-api'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'

import { PageHeader, formatMb } from '@kinotic-ai/frontend-common'

import CapacityBar from '@/components/CapacityBar.vue'
import LogLevelDialog from '@/components/LogLevelDialog.vue'
import WorkloadStateCard from '@/components/WorkloadStateCard.vue'
import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'

const clusterInfo = ref<KinoticClusterInfo | null>(null)
const clusterError = ref<string | null>(null)
const organizationCount = ref<number | null>(null)
const workerNodeCount = ref<number | null>(null)
const workerNodes = ref<VmNode[]>([])

// A workload can only be placed on an ONLINE node, so an offline node's free capacity is not
// the platform's to hand out — counting it reports headroom no placement can actually use.
const onlineNodes = computed(() => workerNodes.value.filter(node => node.status?.type === VmNodeStatusType.ONLINE))

const capacityRows = computed(() => {
  const total = { cpus: 0, memoryMb: 0, diskMb: 0 }
  const used = { cpus: 0, memoryMb: 0, diskMb: 0 }
  for (const node of onlineNodes.value) {
    total.cpus += node.totalCpus
    total.memoryMb += node.totalMemoryMb
    total.diskMb += node.totalDiskMb
    used.cpus += node.totalCpus - node.availableCpus
    used.memoryMb += node.totalMemoryMb - node.availableMemoryMb
    used.diskMb += node.totalDiskMb - node.availableDiskMb
  }
  const pct = (allocated: number, all: number) => all > 0 ? Math.round((allocated / all) * 100) : 0
  return [
    { label: 'CPU', text: `${used.cpus} / ${total.cpus} vCPU`, pct: pct(used.cpus, total.cpus) },
    { label: 'Memory', text: `${formatMb(used.memoryMb)} / ${formatMb(total.memoryMb)}`, pct: pct(used.memoryMb, total.memoryMb) },
    { label: 'Disk', text: `${formatMb(used.diskMb)} / ${formatMb(total.diskMb)}`, pct: pct(used.diskMb, total.diskMb) }
  ]
})

const logLevelNodeId = ref<string | null>(null)
const logLevelVisible = ref(false)

interface Stat {
  label: string
  value: string
  description: string
  tag?: string
  /** Route the tile navigates to on click; unset renders a static tile. */
  to?: string
  icon?: string
  accent?: StatTileAccent
}

const stats = computed<Stat[]>(() => [
  {
    label: 'Server nodes',
    value: clusterInfo.value?.serverNodeCount?.toString() ?? '—',
    description: 'kinotic-server instances in the cluster',
    icon: 'pi-server',
    accent: 'sky'
  },
  {
    label: 'Cluster state',
    value: clusterInfo.value?.clusterState ?? '—',
    description: 'Whether the cluster is serving requests',
    tag: clusterInfo.value ? (clusterInfo.value.active ? 'success' : 'danger') : 'secondary',
    icon: 'pi-shield',
    accent: clusterInfo.value && !clusterInfo.value.active ? 'red' : 'green'
  },
  {
    label: 'Topology version',
    value: clusterInfo.value?.topologyVersion?.toString() ?? '—',
    description: 'Increments each time a node joins or leaves',
    icon: 'pi-sync',
    accent: 'violet'
  },
  {
    label: 'Worker nodes',
    value: workerNodeCount.value === null ? '—' : `${onlineNodes.value.length} / ${workerNodeCount.value}`,
    description: 'VmManager nodes online, of those registered',
    to: '/worker-nodes',
    icon: 'pi-box',
    accent: workerNodeCount.value !== null && onlineNodes.value.length === 0 ? 'red' : 'amber'
  },
  {
    label: 'Organizations',
    value: organizationCount.value?.toString() ?? '—',
    description: 'Organizations registered on the platform',
    to: '/organizations',
    icon: 'pi-building',
    accent: 'teal'
  }
])

function openLogLevel(nodeId: string) {
  logLevelNodeId.value = nodeId
  logLevelVisible.value = true
}

onMounted(async () => {
  try {
    clusterInfo.value = await Kinotic.clusterInfo.getClusterInfo()
  } catch (err) {
    clusterError.value = err instanceof Error ? err.message : 'Failed to load cluster info'
  }
  try {
    organizationCount.value = await Kinotic.systemOrganizations.countOrganizations()
  } catch {
    // The tile shows an em dash; the count is cosmetic and must not block the page
  }
  try {
    // One fetch feeds the worker tile and the capacity card
    const page = await Kinotic.vmNodes.findAll(Pageable.create(0, 100))
    workerNodes.value = page.content ?? []
    workerNodeCount.value = page.totalElements ?? workerNodes.value.length
  } catch {
    // Same em-dash fallback as the organization count; the capacity card shows its empty state
  }
})
</script>
