<template>
  <div class="flex flex-col">
    <PageHeader title="Worker nodes"
                description="VmManager nodes that host workloads, and the capacity each has left.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
                :loading="loadingNodes" @click="refreshAll" />
      </template>
    </PageHeader>

    <Message v-if="nodesError" severity="error" :closable="false" class="mb-4">{{ nodesError }}</Message>

    <div v-else-if="nodes.length === 0 && !loadingNodes"
         class="p-6 border border-dashed border-surface rounded-lg text-muted-color">
      No worker nodes have registered with the orchestrator
    </div>

    <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
      <div v-for="node in nodes" :key="node.id" class="flex flex-col gap-2 p-4 border border-surface rounded-lg">
        <div class="flex items-center justify-between">
          <span class="font-semibold">{{ node.name }}</span>
          <Tag :value="node.status.type" :severity="nodeSeverity(node.status.type)" />
        </div>
        <Tag :value="node.providerType" severity="secondary" class="self-end -mt-1" />
        <span class="break-all font-mono text-xs text-muted-color">{{ node.hostname }}</span>

        <div class="flex flex-col gap-2 mt-1">
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>CPU</span>
              <span>{{ node.totalCpus - node.availableCpus }} / {{ node.totalCpus }} vCPU</span>
            </div>
            <CapacityBar :pct="percentOf(node.totalCpus - node.availableCpus, node.totalCpus)" />
          </div>
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>Memory</span>
              <span>{{ formatMb(node.totalMemoryMb - node.availableMemoryMb) }} / {{ formatMb(node.totalMemoryMb) }}</span>
            </div>
            <CapacityBar :pct="percentOf(node.totalMemoryMb - node.availableMemoryMb, node.totalMemoryMb)" />
          </div>
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>Disk</span>
              <span>{{ formatMb(node.totalDiskMb - node.availableDiskMb) }} / {{ formatMb(node.totalDiskMb) }}</span>
            </div>
            <CapacityBar :pct="percentOf(node.totalDiskMb - node.availableDiskMb, node.totalDiskMb)" />
          </div>
        </div>

        <Message v-if="node.status.healthMessage" severity="warn" :closable="false" class="mt-1 text-xs">
          {{ node.status.healthMessage }}
        </Message>

        <div class="text-xs text-muted-color mt-1">Last seen: {{ formatEpochDateTime(node.lastSeen) }}</div>
      </div>
    </div>

    <h2 class="text-lg font-semibold mt-6 mb-3">Workloads</h2>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :default-sort="DEFAULT_SORT"
      :is-show-add-new="false"
      :disable-modifications="true"
      :row-actions="rowActions"
      empty-state-text="No workloads"
      @update:search="tableSearch = $event"
    >
      <template #item.node="{ item }">
        {{ item.node || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="workloadSeverity(item.status)" />
      </template>

      <template #item.image="{ item }">
        <span class="font-mono text-sm">{{ item.image }}</span>
      </template>

      <template #item.organizationId="{ item }">
        <span v-if="item.organizationId" class="font-mono text-sm">{{ item.organizationId }}</span>
        <span v-else>platform</span>
      </template>

      <template #item.created="{ item }">
        {{ formatEpochDateTime(item.created) }}
      </template>
    </CrudTable>

    <WorkloadLogsDialog
      v-if="logsWorkload"
      v-model:visible="logsVisible"
      :workload-id="logsWorkload.id"
      :workload-name="logsWorkload.name"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'

import { Direction, FunctionalIterablePage, Kinotic, Order, Pageable, Sort,
         type IterablePage, type Page } from '@kinotic-ai/core'
import { VmNodeStatusType, type VmNode } from '@kinotic-ai/system-api'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import {
  CrudTable,
  PageHeader,
  formatMb,
  DatetimeUtil,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import CapacityBar from '@/components/CapacityBar.vue'
import WorkloadLogsDialog from '@/components/WorkloadLogsDialog.vue'

interface WorkloadRow extends DescriptiveIdentifiable {
  id: string
  name: string
  node: string
  status: WorkloadStatus
  image: string
  resources: string
  organizationId: string | null
  created: number | null
  autoRemove: boolean
}

const DEFAULT_SORT = [new Order('created', Direction.DESC)]

const headers: CrudHeader[] = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'node', header: 'Node', sortable: false },
  { field: 'status', header: 'Status', sortable: false },
  { field: 'image', header: 'Image', sortable: false },
  { field: 'resources', header: 'Resources', sortable: false },
  { field: 'organizationId', header: 'Organization', sortable: false },
  { field: 'created', header: 'Created', sortable: true }
]

const confirm = useConfirm()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const nodes = ref<VmNode[]>([])
const loadingNodes = ref(false)
const nodesError = ref<string | null>(null)
// Resolves a workload's nodeId to the node's name in the table
const nodeNames = ref<Record<string, string>>({})

const logsWorkload = ref<{ id: string; name: string } | null>(null)
const logsVisible = ref(false)

// Nodes that can actually take a workload belong at the top. status.type is a keyword, and its
// three values sort ONLINE, OFFLINE, DRAINING descending, so descending is the order the page
// wants; name breaks ties so cards hold a stable position across refreshes.
const NODE_SORT = new Sort()
NODE_SORT.orders = [new Order('status.type', Direction.DESC), new Order('name', Direction.ASC)]

async function loadNodes() {
  loadingNodes.value = true
  nodesError.value = null
  try {
    const page = await Kinotic.vmNodes.findAll(Pageable.create(0, 100, NODE_SORT))
    nodes.value = page.content ?? []
    nodeNames.value = Object.fromEntries(nodes.value.map(node => [node.id, node.name]))
  } catch (err) {
    nodesError.value = err instanceof Error ? err.message : 'Failed to load nodes'
  } finally {
    loadingNodes.value = false
  }
}

const { crudTable, tableSearch, dataSource, refreshTable, run } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const workloads = searchText
      ? await Kinotic.workloads.search(searchText, pageable)
      : await Kinotic.workloads.findAll(pageable)
  const page: Page<DescriptiveIdentifiable> = {
    content: (workloads.content ?? []).map(toRow),
    totalElements: workloads.totalElements,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function toRow(workload: Workload): WorkloadRow {
  return {
    id: workload.id ?? '',
    name: workload.name,
    node: workload.nodeId ? nodeNames.value[workload.nodeId] ?? workload.nodeId : '',
    status: workload.status,
    image: workload.image,
    resources: `${workload.vcpus} vCPU · ${formatMb(workload.memoryMb)} · ${formatMb(workload.diskSizeMb)}`,
    organizationId: workload.organizationId,
    created: workload.created,
    autoRemove: workload.autoRemove
  }
}

function rowActions(item: WorkloadRow): MenuItem[] {
  const actions: MenuItem[] = [
    {
      label: 'View logs',
      icon: 'pi pi-align-left',
      command: () => {
        logsWorkload.value = { id: item.id, name: item.name }
        logsVisible.value = true
      }
    }
  ]
  if (item.status === WorkloadStatus.RUNNING || item.status === WorkloadStatus.STARTING) {
    actions.push({
      label: 'Stop',
      icon: 'pi pi-stop-circle',
      command: () => run(
          async () => { await Kinotic.workloadOrchestration.stopWorkload(item.id) },
          'Workload stopping',
          'Failed to stop workload')
    })
  }
  // A workload stopped with autoRemove has no VM left to restart
  if (item.status === WorkloadStatus.STOPPED && !item.autoRemove) {
    actions.push({
      label: 'Restart',
      icon: 'pi pi-replay',
      command: () => run(
          async () => { await Kinotic.workloadOrchestration.restartWorkload(item.id) },
          'Workload restarting',
          'Failed to restart workload')
    })
  }
  actions.push({
    label: 'Destroy',
    icon: 'pi pi-trash',
    command: () => confirm.require({
      header: 'Confirm destroy',
      message: `Destroy workload ${item.name}? Its VM and disk are removed permanently.`,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Destroy', severity: 'danger' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: () => run(
          async () => { await Kinotic.workloadOrchestration.destroyWorkload(item.id) },
          'Workload destroyed',
          'Failed to destroy workload').then(loadNodes)
    })
  })
  return actions
}

function refreshAll() {
  loadNodes()
  refreshTable()
}

function nodeSeverity(status: VmNodeStatusType): string {
  let ret: string
  if (status === VmNodeStatusType.ONLINE) {
    ret = 'success'
  } else if (status === VmNodeStatusType.DRAINING) {
    ret = 'warn'
  } else {
    ret = 'danger'
  }
  return ret
}

function workloadSeverity(status: WorkloadStatus): string {
  let ret: string
  if (status === WorkloadStatus.RUNNING) {
    ret = 'success'
  } else if (status === WorkloadStatus.STARTING || status === WorkloadStatus.PENDING) {
    ret = 'info'
  } else if (status === WorkloadStatus.STOPPING) {
    ret = 'warn'
  } else if (status === WorkloadStatus.FAILED) {
    ret = 'danger'
  } else {
    ret = 'secondary'
  }
  return ret
}

function percentOf(allocated: number, total: number): number {
  return total > 0 ? Math.round((allocated / total) * 100) : 0
}


onMounted(loadNodes)
</script>
