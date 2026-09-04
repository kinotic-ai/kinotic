<template>
  <div class="flex flex-col">
    <PageHeader title="Worker nodes"
                description="VmManager nodes that host workloads, and the capacity each has left.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
                :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <div v-if="nodes.length === 0 && !loading"
         class="rounded-lg border border-dashed border-surface p-6 text-muted-color">
      No worker nodes have registered with the orchestrator
    </div>
    <div v-else-if="shown.length === 0 && !loading"
         class="rounded-lg border border-dashed border-surface p-6 text-muted-color">
      No worker node is {{ statusFilter?.toLowerCase() }}
    </div>

    <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
      <RouterLink v-for="node in shown" :key="node.id" :to="`/worker-nodes/${encodeURIComponent(node.id)}`"
                  class="flex flex-col gap-2 rounded-lg border border-surface p-4 text-color no-underline transition-colors hover:bg-emphasis">
        <div class="flex items-center justify-between gap-2">
          <span class="truncate font-semibold">{{ node.name }}</span>
          <Tag :value="node.status.type" :severity="nodeSeverity(node.status.type)" />
        </div>
        <div class="flex flex-wrap items-center gap-2 text-xs text-muted-color">
          <Tag :value="node.providerType" severity="secondary" />
          <span class="break-all font-mono">{{ node.hostname }}</span>
        </div>

        <div v-if="node.status.type === VmNodeStatusType.OFFLINE" class="py-2 text-sm text-muted-color">
          No heartbeat since {{ formatEpochDateTime(node.lastSeen) }}.
          {{ workloadsOn(node.id).length > 0 ? `Its ${workloadsOn(node.id).length} workloads are unreachable with it.` : '' }}
        </div>
        <CapacityRows v-else :capacity="capacityOf([node])" class="mt-1" />

        <Message v-if="node.status.healthMessage" severity="warn" :closable="false" class="mt-1 text-xs">
          {{ node.status.healthMessage }}
        </Message>

        <div class="mt-1 flex flex-wrap justify-between gap-x-3 text-xs text-muted-color">
          <span>{{ runningOn(node.id) }} running · {{ workloadsOn(node.id).length }} workloads</span>
          <span>Last seen {{ formatEpochDateTime(node.lastSeen) }}</span>
        </div>
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, PageHeader, errorMessage } from '@kinotic-ai/frontend-common'

import CapacityRows from '@/components/CapacityRows.vue'
import StatusChips, { type StatusChip } from '@/components/StatusChips.vue'
import { capacityOf, loadNodes, nodeSeverity } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

const NODE_STATES = [VmNodeStatusType.ONLINE, VmNodeStatusType.DRAINING, VmNodeStatusType.OFFLINE]

const route = useRoute()
const router = useRouter()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const nodes = ref<VmNode[]>([])
const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// The filter lives in the URL so a tile can link straight to the offline nodes
const statusFilter = computed<string | null>({
  get: () => NODE_STATES.includes(route.query.status as VmNodeStatusType) ? route.query.status as string : null,
  set: value => { router.replace({ query: { ...route.query, status: value ?? undefined } }) }
})

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null, count: nodes.value.length },
  ...NODE_STATES.map(state => ({
    label: state.charAt(0) + state.slice(1).toLowerCase(),
    value: state,
    count: nodes.value.filter(node => node.status.type === state).length
  }))
])

const shown = computed(() => statusFilter.value
    ? nodes.value.filter(node => node.status.type === statusFilter.value)
    : nodes.value)

function workloadsOn(nodeId: string): Workload[] {
  return workloads.value.filter(workload => workload.nodeId === nodeId)
}

function runningOn(nodeId: string): number {
  return workloadsOn(nodeId).filter(workload => workload.status === WorkloadStatus.RUNNING).length
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [nodeList, workloadList] = await Promise.all([loadNodes(), scanWorkloads({})])
    nodes.value = nodeList
    workloads.value = workloadList
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load worker nodes')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
