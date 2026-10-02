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

    <!-- the fleet at a glance: what the online nodes have placed of what they offer -->
    <section v-if="nodes.length > 0"
             class="mb-4 grid gap-5 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 sm:grid-cols-2 lg:grid-cols-[auto_repeat(3,minmax(0,1fr))] dark:border-surface-700 dark:bg-surface-800/30">
      <div class="flex items-center gap-3 lg:pr-3">
        <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', TINTS.ink]">
          <ServerCog :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="leading-tight">
          <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Fleet online</div>
          <div class="mt-0.5 text-sm"><b class="text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ onlineNodes.length }}</b>
            <span class="text-muted-color"> of {{ nodes.length }} nodes</span></div>
        </div>
      </div>
      <div v-for="row in fleetRows" :key="row.label" class="min-w-0">
        <div class="mb-1 flex justify-between gap-2 text-xs">
          <span class="font-semibold uppercase tracking-wider text-muted-color">{{ row.label }}</span>
          <span class="truncate tabular-nums text-surface-700 dark:text-surface-200">{{ row.text }}</span>
        </div>
        <CapacityBar :pct="row.pct" :hover="{ heading: row.label, name: 'Allocated', value: `${row.text} · ${row.pct}%` }" />
      </div>
    </section>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <div v-if="nodes.length === 0 && !loading"
         class="rounded-xl border border-dashed border-surface-200 p-6 text-sm text-muted-color dark:border-surface-700">
      No worker nodes have registered with the orchestrator
    </div>
    <div v-else-if="shown.length === 0 && !loading"
         class="rounded-xl border border-dashed border-surface-200 p-6 text-sm text-muted-color dark:border-surface-700">
      No worker node is {{ statusFilter?.toLowerCase() }}
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <!-- A card opens its node in the drawer; the card the drawer shows stays marked -->
      <div v-for="node in shown" :key="node.id" role="button" tabindex="0" :aria-label="`Open ${node.name}`"
           :class="['relative flex cursor-pointer flex-col gap-3 overflow-hidden rounded-xl border p-5 text-color transition-colors focus-visible:outline-2 focus-visible:outline-offset-2',
                    highlightedNode?.id === node.id
                      ? 'border-surface-300 bg-surface-100 dark:border-surface-600 dark:bg-surface-800/70'
                      : 'border-surface-200 bg-surface-0 hover:border-surface-300 hover:bg-surface-100 dark:border-surface-700 dark:bg-surface-800/30 dark:hover:border-surface-600 dark:hover:bg-surface-800/70']"
           @click="openNode(node)" @keydown.enter="openNode(node)" @mouseenter="hoverNode(node)">
        <!-- a thin line in the node's status colour along the card's top edge -->
        <span :class="['absolute inset-x-0 top-0 h-0.5', STATUS_LINE[nodeHealth(node)]]" aria-hidden="true" />
        <div class="flex items-start gap-3">
          <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', HEARTBEAT_TINTS[nodeHeartbeat(nodeHealth(node))]]">
            <HeartbeatIcon :state="nodeHeartbeat(nodeHealth(node))" :size="20" :stroke-width="1.75" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="truncate font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="node.name">{{ node.name }}</div>
            <div class="flex min-w-0 items-center gap-1.5 text-xs text-muted-color">
              <span class="truncate font-mono" v-tooltip.top="node.hostname">{{ node.hostname }}</span>
              <span class="shrink-0 rounded bg-surface-100 px-1 font-mono text-[0.625rem] text-surface-500 dark:bg-surface-800 dark:text-surface-400">{{ node.providerType }}</span>
            </div>
          </div>
          <Tag :value="nodeHealth(node)" :severity="nodeSeverity(nodeHealth(node))" />
        </div>

        <div v-if="nodeHealth(node) === NodeHealth.UNREACHABLE">
          <p class="text-sm font-medium text-red-700 dark:text-red-400">Not answering its heartbeat</p>
          <p class="mt-0.5 text-[0.8125rem] text-muted-color">
            Nothing new is placed here<template v-if="workloadsOn(node.id).length > 0"> · {{ workloadsOn(node.id).length }} {{ workloadsOn(node.id).length === 1 ? 'workload' : 'workloads' }} unreachable</template>
          </p>
          <!-- what it last reported, faded so it never reads as live -->
          <div class="mt-3 opacity-45 grayscale">
            <div class="mb-2 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Last reported</div>
            <CapacityRows :capacity="capacityOf([node])" />
          </div>
        </div>
        <CapacityRows v-else :capacity="capacityOf([node])" />

        <p v-if="node.healthMessage" class="flex items-start gap-2 text-[0.8125rem] text-amber-700 dark:text-amber-300">
          <TriangleAlert :size="15" :stroke-width="1.75" class="mt-0.5 shrink-0" aria-hidden="true" />
          {{ node.healthMessage }}
        </p>
        <p v-if="node.state.deletionRequested" class="flex items-center gap-2 text-[0.8125rem] text-muted-color">
          <LoaderCircle :size="15" :stroke-width="1.75" class="shrink-0 animate-spin" aria-hidden="true" />
          Deregistering
        </p>

        <div class="mt-auto flex items-center justify-between gap-3 border-t border-surface-100 pt-3 text-xs text-muted-color dark:border-surface-800">
          <span><b class="font-semibold tabular-nums text-surface-900 dark:text-surface-50">{{ runningOn(node.id) }}</b> of {{ workloadsOn(node.id).length }} workloads running</span>
          <span class="whitespace-nowrap" v-tooltip.top="node.lastSeen ? DatetimeUtil.formatEpochDateTime(node.lastSeen) : undefined">
            Seen {{ node.lastSeen ? DatetimeUtil.formatRelativeDate(node.lastSeen).toLowerCase() : 'never' }}
          </span>
        </div>
      </div>
    </div>

    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shown.length"
                    :expand-to="selectedNode ? `/worker-nodes/${encodeURIComponent(selectedNode.id)}` : undefined"
                    expand-label="Open the node's page" @step="stepNode">
      <template #title>
        <template v-if="selectedNode">
          <span class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedNode.name }}</span>
          <Tag :value="nodeHealth(selectedNode)" :severity="nodeSeverity(nodeHealth(selectedNode))" class="shrink-0" />
        </template>
      </template>
      <NodePage v-if="selectedNode" :key="selectedNode.id" :node-id="selectedNode.id" />
    </SteppingDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { LoaderCircle, ServerCog, TriangleAlert } from '@lucide/vue'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, HEARTBEAT_TINTS, HeartbeatIcon, PageHeader, SteppingDrawer, TINTS, errorMessage, formatMb,
         useSteppingDrawer, StatusChips, type StatusChip } from '@kinotic-ai/frontend-common'

import CapacityBar from '@/components/CapacityBar.vue'
import CapacityRows from '@/components/CapacityRows.vue'
import { NodeHealth, capacityOf, formatCpus, loadNodes, nodeHealth, nodeHeartbeat, nodeSeverity, percentOf } from '@/util/nodes'
import { scanWorkloads } from '@/util/workloads'
import NodePage from '@/pages/NodePage.vue'

const NODE_STATES = [NodeHealth.ONLINE, NodeHealth.DRAINING, NodeHealth.UNREACHABLE]

/** The colour of each status's line along a card's top edge, as the status tags use. */
const STATUS_LINE: Record<NodeHealth, string> = {
  [NodeHealth.ONLINE]: 'bg-green-500',
  [NodeHealth.DRAINING]: 'bg-amber-500',
  [NodeHealth.UNREACHABLE]: 'bg-red-500',
  [NodeHealth.UNKNOWN]: 'bg-surface-300'
}

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
    count: nodes.value.filter(node => nodeHealth(node) === state).length,
    severity: nodeSeverity(state)
  }))
])

const onlineNodes = computed(() => nodes.value.filter(node => nodeHealth(node) === NodeHealth.ONLINE))

// The fleet's capacity counts only what the online nodes offer; an unreachable node's is not placeable
const fleetRows = computed(() => {
  const c = capacityOf(onlineNodes.value)
  return [
    { label: 'CPU', text: `${formatCpus(c.usedCpus)} / ${c.cpus} CPU`, pct: percentOf(c.usedCpus, c.cpus) },
    { label: 'Memory', text: `${formatMb(c.usedMemoryMb)} / ${formatMb(c.memoryMb)}`, pct: percentOf(c.usedMemoryMb, c.memoryMb) },
    { label: 'Disk', text: `${formatMb(c.usedDiskMb)} / ${formatMb(c.diskMb)}`, pct: percentOf(c.usedDiskMb, c.diskMb) }
  ]
})

const shown = computed(() => statusFilter.value
    ? nodes.value.filter(node => nodeHealth(node) === statusFilter.value)
    : nodes.value)

const { selected: selectedNode, visible: drawerVisible, position, open: openNode, step: stepNode,
        highlighted: highlightedNode, hover: hoverNode } = useSteppingDrawer(shown, node => node.id)

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
