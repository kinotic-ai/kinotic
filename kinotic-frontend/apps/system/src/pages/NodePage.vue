<template>
  <div class="flex flex-col">
    <PageHeader :title="node?.name ?? nodeId">
      <template #eyebrow>
        <RouterLink to="/worker-nodes" class="hover:underline">Worker nodes</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate">{{ node?.name ?? nodeId }}</span>
      </template>
      <template #actions>
        <Tag v-if="node" :value="node.status.type" :severity="nodeSeverity(node.status.type)" />
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <template v-if="node">
      <Message v-if="node.status.type === VmNodeStatusType.DRAINING" severity="warn" :closable="false" class="mb-4">
        <b>Draining.</b> {{ node.status.healthMessage ?? 'The node reported a problem.' }}
        The orchestrator places nothing new here until the node reports no problems; its
        {{ workloads.length }} workloads keep running.
      </Message>
      <Message v-else-if="node.status.type === VmNodeStatusType.OFFLINE" severity="error" :closable="false" class="mb-4">
        <b>Offline.</b> No heartbeat since {{ formatEpochDateTime(node.lastSeen) }}. A node that
        reconnects is online again with its next heartbeat.
      </Message>

      <div class="flex flex-col gap-4">
        <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
          <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
        </div>

        <div class="rounded-lg border border-surface p-4">
          <div class="mb-3 flex items-start justify-between gap-3">
            <div>
              <h2 class="text-base font-semibold">Workloads on this node</h2>
              <p class="text-xs text-muted-color">Click a row for the workload; its menu shows its logs, stops, restarts or destroys it.</p>
            </div>
            <RouterLink :to="{ path: '/workloads', query: { node: nodeId } }"
                        class="whitespace-nowrap text-sm text-muted-color hover:text-color">Filter workloads</RouterLink>
          </div>
          <WorkloadsTable :workloads="workloads" :scope="{}" :show-node="false" @changed="load" />
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <div class="rounded-lg border border-surface p-4">
            <h2 class="mb-2 text-base font-semibold">Details</h2>
            <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
              <dt class="text-muted-color">Node id</dt>
              <dd class="break-all font-mono">{{ node.id }}</dd>
              <dt class="text-muted-color">Host</dt>
              <dd class="break-all font-mono">{{ node.hostname }}</dd>
              <dt class="text-muted-color">Provider</dt>
              <dd>{{ node.providerType }}</dd>
              <dt class="text-muted-color">Workload data dir</dt>
              <dd class="break-all font-mono">{{ node.workloadDataDir ?? '—' }}</dd>
              <dt class="text-muted-color">Last heartbeat</dt>
              <dd>{{ formatEpochDateTime(node.lastSeen) }}</dd>
            </dl>
          </div>
          <div class="rounded-lg border border-surface p-4">
            <h2 class="text-base font-semibold">Capacity</h2>
            <p class="mb-3 text-xs text-muted-color">What the node promised at registration, less what is placed on it.</p>
            <CapacityRows :capacity="capacityOf([node])" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { Kinotic } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, PageHeader, errorMessage, formatMb } from '@kinotic-ai/frontend-common'

import CapacityRows from '@/components/CapacityRows.vue'
import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { capacityOf, nodeSeverity, percentOf } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

/**
 * One worker node: its health explained, its capacity, the workloads placed on it, and what it
 * reported at registration.
 */
const props = defineProps<{
  nodeId: string
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const node = ref<VmNode | null>(null)
const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

interface Stat {
  label: string
  value: string
  description: string
  to?: object
  icon?: string
  accent?: StatTileAccent
}

const stats = computed<Stat[]>(() => {
  const n = node.value
  if (!n) return []
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  return [
    {
      label: 'CPU',
      value: `${percentOf(n.totalCpus - n.availableCpus, n.totalCpus)}%`,
      description: `${n.totalCpus - n.availableCpus} of ${n.totalCpus} vCPU allocated`,
      icon: 'pi-microchip',
      accent: 'sky'
    },
    {
      label: 'Memory',
      value: `${percentOf(n.totalMemoryMb - n.availableMemoryMb, n.totalMemoryMb)}%`,
      description: `${formatMb(n.totalMemoryMb - n.availableMemoryMb)} of ${formatMb(n.totalMemoryMb)}`,
      icon: 'pi-database',
      accent: 'violet'
    },
    {
      label: 'Disk',
      value: `${percentOf(n.totalDiskMb - n.availableDiskMb, n.totalDiskMb)}%`,
      description: `${formatMb(n.totalDiskMb - n.availableDiskMb)} of ${formatMb(n.totalDiskMb)}`,
      icon: 'pi-inbox',
      accent: 'teal'
    },
    {
      label: 'Workloads',
      value: `${running}`,
      description: `running of ${workloads.value.length} placed here`,
      to: { path: '/workloads', query: { node: props.nodeId } },
      icon: 'pi-box',
      accent: 'green'
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const [found, placed] = await Promise.all([
      Kinotic.vmNodes.findById(props.nodeId),
      scanWorkloads({}, { nodeId: props.nodeId })
    ])
    node.value = found
    workloads.value = placed
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the worker node')
  } finally {
    loading.value = false
  }
}

watch(() => props.nodeId, load, { immediate: true })
</script>
