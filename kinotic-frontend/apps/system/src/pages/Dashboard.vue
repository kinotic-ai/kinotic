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
          Allocated versus total across every worker node.
        </p>
        <div v-if="workerNodes.length === 0" class="py-6 text-center text-sm text-muted-color">
          No worker nodes registered
        </div>
        <div v-else class="flex flex-col gap-3">
          <div v-for="row in capacityRows" :key="row.label">
            <div class="mb-1 flex justify-between text-sm">
              <span class="text-muted-color">{{ row.label }}</span>
              <span>{{ row.text }}</span>
            </div>
            <div class="h-3 rounded bg-surface-200 dark:bg-surface-800" :title="`${row.pct}% allocated`">
              <div class="h-full rounded" :style="{ width: row.pct + '%', background: capacityColor }" />
            </div>
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-surface p-4">
        <div class="flex items-start justify-between">
          <div>
            <h2 class="text-base font-semibold">Workloads</h2>
            <p class="mb-4 text-xs text-muted-color">
              Every workload on the platform, by state.
            </p>
          </div>
          <RouterLink to="/worker-nodes" class="text-sm text-muted-color hover:text-color">View all</RouterLink>
        </div>
        <div v-if="workloadTotal === 0" class="py-6 text-center text-sm text-muted-color">
          No workloads
        </div>
        <template v-else>
          <div class="flex h-3 gap-[2px] overflow-hidden rounded">
            <div
              v-for="seg in workloadSegments.filter(s => s.count > 0)"
              :key="seg.label"
              :style="{ width: (seg.count / workloadTotal * 100) + '%', background: seg.color }"
              :title="`${seg.label}: ${seg.count}`"
            />
          </div>
          <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1">
            <div v-for="seg in workloadSegments" :key="seg.label" class="flex items-center gap-1.5 text-sm">
              <span class="h-2.5 w-2.5 rounded-full" :style="{ background: seg.color }" />
              <span class="text-muted-color">{{ seg.label }}</span>
              <span class="font-medium">{{ seg.count }}</span>
            </div>
          </div>
        </template>
      </div>
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
                <Button label="Log level" icon="pi pi-sliders-h" severity="secondary" text size="small"
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
import { RouterLink } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus, type KinoticClusterInfo, type VmNode } from '@kinotic-ai/os-api'

import { PageHeader, formatMb, isDark } from '@kinotic-ai/frontend-common'

import LogLevelDialog from '@/components/LogLevelDialog.vue'
import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'

const clusterInfo = ref<KinoticClusterInfo | null>(null)
const clusterError = ref<string | null>(null)
const organizationCount = ref<number | null>(null)
const workerNodeCount = ref<number | null>(null)
const workerNodes = ref<VmNode[]>([])
const workloadCounts = ref<Record<StatusBucket, number>>({ running: 0, starting: 0, stopping: 0, stopped: 0, failed: 0 })

type StatusBucket = 'running' | 'starting' | 'stopping' | 'stopped' | 'failed'

// Theme ramp steps validated for adjacent-pair CVD separation and surface contrast in both
// modes (dark uses the lighter 400 steps). STOPPED is a deliberate achromatic neutral, and
// every segment carries a labeled legend row, so identity is never color alone.
const STATUS_SERIES: { bucket: StatusBucket; label: string; light: string; dark: string }[] = [
  { bucket: 'running', label: 'Running', light: '#22C55E', dark: '#4ADE80' },
  { bucket: 'starting', label: 'Starting', light: '#0EA5E9', dark: '#38BDF8' },
  { bucket: 'stopping', label: 'Stopping', light: '#F97316', dark: '#FB923C' },
  { bucket: 'stopped', label: 'Stopped', light: '#6B7280', dark: '#9CA3AF' },
  { bucket: 'failed', label: 'Failed', light: '#EF4444', dark: '#F87171' }
]

const BUCKET_BY_STATUS: Record<WorkloadStatus, StatusBucket> = {
  [WorkloadStatus.RUNNING]: 'running',
  [WorkloadStatus.PENDING]: 'starting',
  [WorkloadStatus.STARTING]: 'starting',
  [WorkloadStatus.STOPPING]: 'stopping',
  [WorkloadStatus.STOPPED]: 'stopped',
  [WorkloadStatus.FAILED]: 'failed'
}

const capacityColor = computed(() => isDark.value ? '#38BDF8' : '#0EA5E9')

const workloadSegments = computed(() => STATUS_SERIES.map(series => ({
  label: series.label,
  count: workloadCounts.value[series.bucket],
  color: isDark.value ? series.dark : series.light
})))

const workloadTotal = computed(() => workloadSegments.value.reduce((sum, seg) => sum + seg.count, 0))

const capacityRows = computed(() => {
  const total = { cpus: 0, memoryMb: 0, diskMb: 0 }
  const used = { cpus: 0, memoryMb: 0, diskMb: 0 }
  for (const node of workerNodes.value) {
    total.cpus += node.totalCpus
    total.memoryMb += node.totalMemoryMb
    total.diskMb += node.totalDiskMb
    used.cpus += node.allocatedCpus
    used.memoryMb += node.allocatedMemoryMb
    used.diskMb += node.allocatedDiskMb
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
    value: workerNodeCount.value?.toString() ?? '—',
    description: 'VmManager nodes available to host workloads',
    to: '/worker-nodes',
    icon: 'pi-box',
    accent: 'amber'
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
  try {
    await loadWorkloadCounts()
  } catch {
    // The workloads card shows its empty state
  }
})

async function loadWorkloadCounts() {
  const counts: Record<StatusBucket, number> = { running: 0, starting: 0, stopping: 0, stopped: 0, failed: 0 }
  // Buckets are counted client-side from pages; the loop is bounded, so a platform with
  // more than 1000 workloads undercounts — revisit with server-side aggregation then
  const pageSize = 100
  for (let pageNumber = 0; pageNumber < 10; pageNumber++) {
    const page = await Kinotic.workloads.findAll(Pageable.create(pageNumber, pageSize))
    const content = page.content ?? []
    for (const workload of content) {
      counts[BUCKET_BY_STATUS[workload.status]] += 1
    }
    if (content.length < pageSize) {
      break
    }
  }
  workloadCounts.value = counts
}
</script>
