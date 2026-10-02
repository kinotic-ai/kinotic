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
         class="rounded-xl border border-dashed border-surface-200 p-6 text-sm text-muted-color dark:border-surface-700">
      No worker nodes have registered with the orchestrator
    </div>
    <div v-else-if="shown.length === 0 && !loading"
         class="rounded-xl border border-dashed border-surface-200 p-6 text-sm text-muted-color dark:border-surface-700">
      No worker node is {{ statusFilter?.toLowerCase() }}
    </div>

    <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
      <RouterLink v-for="node in shown" :key="node.id" :to="`/worker-nodes/${encodeURIComponent(node.id)}`"
                  class="flex flex-col gap-3 rounded-xl border border-surface-200 bg-surface-0 p-5 text-color no-underline transition-colors hover:border-surface-300 hover:bg-surface-100 dark:border-surface-700 dark:bg-surface-800/30 dark:hover:border-surface-600 dark:hover:bg-surface-800/70">
        <div class="flex items-start gap-3">
          <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.orange]">
            <Server :size="18" :stroke-width="1.75" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0">{{ node.name }}</div>
            <div class="truncate font-mono text-xs text-muted-color" v-tooltip.top="node.hostname">{{ node.hostname }}</div>
          </div>
          <Tag :value="nodeHealth(node)" :severity="nodeSeverity(nodeHealth(node))" />
        </div>

        <div v-if="nodeHealth(node) === NodeHealth.UNREACHABLE" class="text-sm text-muted-color">
          {{ nodeUnreachable(node)?.message }}. Nothing is placed here until its next heartbeat.
          {{ workloadsOn(node.id).length > 0 ? `Its ${workloadsOn(node.id).length} workloads are unreachable with it.` : '' }}
        </div>
        <CapacityRows v-else :capacity="capacityOf([node])" />

        <Message v-if="node.healthMessage" severity="warn" :closable="false" class="text-xs">
          {{ node.healthMessage }}
        </Message>
        <Message v-if="node.state.deletionRequested" severity="info" :closable="false" class="text-xs">
          Deregistering
        </Message>

        <div class="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-surface-200 pt-3 text-xs text-muted-color dark:border-surface-700">
          <span class="flex items-center gap-2">
            <Tag :value="node.providerType" severity="secondary" />
            {{ runningOn(node.id) }} running · {{ workloadsOn(node.id).length }} workloads
          </span>
          <span class="flex items-center gap-1.5">Last seen <TimePill :date="node.lastSeen" /></span>
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
import { Server } from '@lucide/vue'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { PageHeader, TINTS, TimePill, errorMessage } from '@kinotic-ai/frontend-common'

import CapacityRows from '@/components/CapacityRows.vue'
import StatusChips, { type StatusChip } from '@/components/StatusChips.vue'
import { NodeHealth, capacityOf, loadNodes, nodeHealth, nodeSeverity, nodeUnreachable } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'

const NODE_STATES = [NodeHealth.ONLINE, NodeHealth.DRAINING, NodeHealth.UNREACHABLE]

const route = useRoute()
const router = useRouter()

const nodes = ref<VmNode[]>([])
const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// The filter lives in the URL so a tile can link straight to the offline nodes
const statusFilter = computed<string | null>({
  get: () => NODE_STATES.includes(route.query.status as NodeHealth) ? route.query.status as string : null,
  set: value => { router.replace({ query: { ...route.query, status: value ?? undefined } }) }
})

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null, count: nodes.value.length },
  ...NODE_STATES.map(state => ({
    label: state.charAt(0) + state.slice(1).toLowerCase(),
    value: state,
    count: nodes.value.filter(node => nodeHealth(node) === state).length
  }))
])

const shown = computed(() => statusFilter.value
    ? nodes.value.filter(node => nodeHealth(node) === statusFilter.value)
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
