<template>
  <div class="flex flex-col">
    <div class="nodes__header">
      <h1 class="nodes__title">Nodes &amp; workloads</h1>
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
              :loading="loadingNodes" @click="refreshAll" />
    </div>

    <Message v-if="nodesError" severity="error" :closable="false" class="nodes__error">{{ nodesError }}</Message>

    <div v-else-if="nodes.length === 0 && !loadingNodes" class="nodes__empty">
      No nodes have registered with the orchestrator
    </div>

    <div v-else class="nodes__grid">
      <div v-for="node in nodes" :key="node.id" class="node-card">
        <div class="node-card__header">
          <span class="node-card__name">{{ node.name }}</span>
          <Tag :value="node.status" :severity="nodeSeverity(node.status)" />
        </div>
        <div class="node-card__hostname font-mono">{{ node.hostname }}</div>

        <div class="node-card__capacity">
          <div class="node-card__metric">
            <div class="node-card__metric-label">
              <span>CPU</span>
              <span>{{ node.allocatedCpus }} / {{ node.totalCpus }} vCPU</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedCpus, node.totalCpus)" :show-value="false" class="node-card__bar" />
          </div>
          <div class="node-card__metric">
            <div class="node-card__metric-label">
              <span>Memory</span>
              <span>{{ formatMb(node.allocatedMemoryMb) }} / {{ formatMb(node.totalMemoryMb) }}</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedMemoryMb, node.totalMemoryMb)" :show-value="false" class="node-card__bar" />
          </div>
          <div class="node-card__metric">
            <div class="node-card__metric-label">
              <span>Disk</span>
              <span>{{ formatMb(node.allocatedDiskMb) }} / {{ formatMb(node.totalDiskMb) }}</span>
            </div>
            <ProgressBar :value="percentOf(node.allocatedDiskMb, node.totalDiskMb)" :show-value="false" class="node-card__bar" />
          </div>
        </div>

        <div class="node-card__last-seen">Last seen: {{ formatEpochDateTime(node.lastSeen) }}</div>
      </div>
    </div>

    <h2 class="nodes__subtitle">Workloads</h2>

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

<style scoped>
.nodes__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.25rem;
}

.nodes__title {
  font-size: 1.4rem;
  font-weight: 600;
}

.nodes__subtitle {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 1.5rem 0 0.75rem;
}

.nodes__error {
  margin-bottom: 1rem;
}

.nodes__empty {
  padding: 1.5rem;
  border: 1px dashed var(--p-content-border-color);
  border-radius: 8px;
  color: var(--p-text-muted-color);
}

.nodes__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: 1rem;
}

.node-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: 8px;
}

.node-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.node-card__name {
  font-weight: 600;
}

.node-card__hostname {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
}

.node-card__capacity {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.node-card__metric-label {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  margin-bottom: 0.2rem;
}

.node-card__bar {
  height: 6px;
}

.node-card__last-seen {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
  margin-top: 0.25rem;
}
</style>
