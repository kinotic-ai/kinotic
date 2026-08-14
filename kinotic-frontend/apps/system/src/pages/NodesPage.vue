<template>
  <div class="flex flex-col">
    <div class="flex items-center justify-between mb-5">
      <h1 class="text-2xl font-semibold">Worker nodes</h1>
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
              :loading="loadingNodes" @click="refreshAll" />
    </div>

    <Message v-if="nodesError" severity="error" :closable="false" class="mb-4">{{ nodesError }}</Message>

    <div v-else-if="nodes.length === 0 && !loadingNodes"
         class="p-6 border border-dashed border-surface rounded-lg text-muted-color">
      No worker nodes have registered with the orchestrator
    </div>

    <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
      <div v-for="node in nodes" :key="node.id" class="flex flex-col gap-2 p-4 border border-surface rounded-lg">
        <div class="flex items-center justify-between">
          <span class="font-semibold">{{ node.name }}</span>
          <Tag :value="node.status" :severity="nodeSeverity(node.status)" />
        </div>
        <div class="font-mono text-xs text-muted-color">{{ node.hostname }}</div>

        <div class="flex flex-col gap-2 mt-1">
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>CPU</span>
              <span>{{ node.allocatedCpus }} / {{ node.totalCpus }} vCPU</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedCpus, node.totalCpus)" :show-value="false" class="!h-1.5" />
          </div>
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>Memory</span>
              <span>{{ formatMb(node.allocatedMemoryMb) }} / {{ formatMb(node.totalMemoryMb) }}</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedMemoryMb, node.totalMemoryMb)" :show-value="false" class="!h-1.5" />
          </div>
          <div>
            <div class="flex justify-between text-xs mb-1">
              <span>Disk</span>
              <span>{{ formatMb(node.allocatedDiskMb) }} / {{ formatMb(node.totalDiskMb) }}</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedDiskMb, node.totalDiskMb)" :show-value="false" class="!h-1.5" />
          </div>
        </div>

        <div class="text-xs text-muted-color mt-1">Last seen: {{ formatEpochDateTime(node.lastSeen) }}</div>
      </div>
    </div>

    <h2 class="text-lg font-semibold mt-6 mb-3">Workloads</h2>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
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
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'

import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage, type Page } from '@kinotic-ai/core'
import { VmNodeStatus, WorkloadStatus, type VmNode, type Workload } from '@kinotic-ai/os-api'
import {
  CrudTable,
  DatetimeUtil,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

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

async function loadNodes() {
  loadingNodes.value = true
  nodesError.value = null
  try {
    const page = await Kinotic.vmNodes.findAll(Pageable.create(0, 100))
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

function nodeSeverity(status: VmNodeStatus): string {
  let ret: string
  if (status === VmNodeStatus.ONLINE) {
    ret = 'success'
  } else if (status === VmNodeStatus.DRAINING) {
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

function formatMb(mb: number): string {
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`
}

onMounted(loadNodes)
</script>
