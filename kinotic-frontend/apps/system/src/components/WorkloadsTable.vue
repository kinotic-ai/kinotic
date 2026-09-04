<template>
  <div>
    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :default-sort="DEFAULT_SORT"
      :is-show-add-new="false"
      :disable-modifications="true"
      :enable-row-hover="true"
      :row-actions="rowActions"
      empty-state-text="No workloads"
      @update:search="tableSearch = $event"
      @on-row-click="open"
    >
      <template #item.name="{ item }">
        <span class="block max-w-[16rem] truncate" :title="item.name">{{ item.name }}</span>
        <span v-if="!item.detached" class="block text-xs text-muted-color">one-off</span>
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="workloadSeverity(item.status)" />
      </template>

      <template #item.node="{ item }">
        <RouterLink v-if="item.nodeId" :to="`/worker-nodes/${encodeURIComponent(item.nodeId)}`"
                    class="hover:underline" @click.stop>{{ item.node }}</RouterLink>
        <span v-else>—</span>
      </template>

      <template #item.owner="{ item }">
        <span v-if="item.owner" class="font-mono text-sm">{{ item.owner }}</span>
        <span v-else class="text-muted-color">{{ ownerFallback }}</span>
      </template>

      <template #item.image="{ item }">
        <span class="block max-w-[16rem] truncate font-mono text-xs" :title="item.image">{{ shortImage(item.image) }}</span>
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
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'

import { Direction, FunctionalIterablePage, Kinotic, Order,
         type IterablePage, type Page, type Pageable, type Sort } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { CrudTable, DatetimeUtil, WorkloadLogsDialog, formatMb, pageNumberOf, useCrudTablePage,
         type CrudHeader, type DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'

import { scopePath, type Scope } from '@/util/scope'
import { shortImage, workloadSeverity } from '@/util/workloads'

/**
 * The given workloads as a searchable, sortable table whose rows open the workload's page
 * under the scope, with a menu that shows its logs, stops, restarts or destroys it. The owner
 * column names what the scope leaves unsaid: the organization on the platform, the
 * application inside an organization, nothing deeper. Emits changed after an action so the
 * caller can read the workloads again.
 */
const props = withDefaults(defineProps<{
  workloads: Workload[]
  scope: Scope
  /** Node names by id, for the node column; an id without one shows as is. */
  nodeNames?: Record<string, string>
  showNode?: boolean
}>(), { nodeNames: () => ({}), showNode: true })

const emit = defineEmits<{
  (e: 'changed'): void
}>()

/** One row of the table. */
interface WorkloadRow extends DescriptiveIdentifiable {
  id: string
  name: string
  status: WorkloadStatus
  nodeId: string | null
  node: string
  owner: string | null
  image: string
  resources: string
  created: number | null
  detached: boolean
  autoRemove: boolean
}

const DEFAULT_SORT = [new Order('created', Direction.DESC)]

const router = useRouter()
const confirm = useConfirm()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const logsWorkload = ref<{ id: string; name: string } | null>(null)
const logsVisible = ref(false)

const ownerHeader = computed<string | null>(() => {
  let ret: string | null
  if (props.scope.applicationId) {
    ret = null
  } else if (props.scope.organizationId) {
    ret = 'Application'
  } else {
    ret = 'Organization'
  }
  return ret
})

const ownerFallback = computed(() => ownerHeader.value === 'Organization' ? 'platform' : 'organization')

const headers = computed<CrudHeader[]>(() => {
  const ret: CrudHeader[] = [
    { field: 'name', header: 'Name', sortable: true },
    { field: 'status', header: 'Status', sortable: true }
  ]
  if (props.showNode) {
    ret.push({ field: 'node', header: 'Node', sortable: false, optional: true })
  }
  if (ownerHeader.value) {
    ret.push({ field: 'owner', header: ownerHeader.value, sortable: false, optional: true })
  }
  ret.push(
    { field: 'image', header: 'Image', sortable: false, optional: true },
    { field: 'resources', header: 'Resources', sortable: false, optional: true },
    { field: 'created', header: 'Created', sortable: true, optional: true }
  )
  return ret
})

const { tableSearch, dataSource, refreshTable, run } = useCrudTablePage(load)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const needle = searchText?.trim().toLowerCase()
  const rows = props.workloads.map(toRow).filter(row => !needle
      || [row.name, row.image, row.node, row.owner].some(value => (value ?? '').toLowerCase().includes(needle)))
  sortRows(rows, pageable.sort)
  const start = pageNumberOf(pageable) * pageable.pageSize
  const page: Page<DescriptiveIdentifiable> = {
    content: rows.slice(start, start + pageable.pageSize),
    totalElements: rows.length,
    cursor: undefined
  }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function sortRows(rows: WorkloadRow[], sort: Sort | null | undefined): void {
  const order = sort?.orders?.[0]
  const property = order?.property ?? 'created'
  const ascending = order?.direction === Direction.ASC
  rows.sort((a, b) => {
    let cmp: number
    if (property === 'name') {
      cmp = a.name.localeCompare(b.name)
    } else if (property === 'status') {
      cmp = a.status.localeCompare(b.status)
    } else {
      cmp = (a.created ?? 0) - (b.created ?? 0)
    }
    return ascending ? cmp : -cmp
  })
}

function ownerOf(workload: Workload): string | null {
  let ret: string | null
  if (ownerHeader.value === 'Organization') {
    ret = workload.organizationId
        ? `${workload.organizationId}${workload.applicationId ? ` / ${workload.applicationId}` : ''}`
        : null
  } else {
    ret = workload.applicationId
  }
  return ret
}

function toRow(workload: Workload): WorkloadRow {
  return {
    id: workload.id ?? '',
    name: workload.name,
    status: workload.status,
    nodeId: workload.nodeId,
    node: workload.nodeId ? props.nodeNames[workload.nodeId] ?? workload.nodeId : '',
    owner: ownerOf(workload),
    image: workload.image,
    resources: `${workload.vcpus} vCPU · ${formatMb(workload.memoryMb)} · ${formatMb(workload.diskSizeMb)}`,
    created: workload.created,
    detached: workload.detached,
    autoRemove: workload.autoRemove
  }
}

function open(row: DescriptiveIdentifiable) {
  router.push(`${scopePath(props.scope)}/workloads/${encodeURIComponent(row.id ?? '')}`)
}

function act(action: () => Promise<unknown>, successMessage: string, failureMessage: string): Promise<void> {
  return run(async () => { await action() }, successMessage, failureMessage).then(() => emit('changed'))
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
      command: () => act(() => Kinotic.workloadOrchestration.stopWorkload(item.id), 'Workload stopping', 'Failed to stop workload')
    })
  }
  // A workload stopped with autoRemove has no VM left to restart
  if ((item.status === WorkloadStatus.STOPPED && !item.autoRemove) || item.status === WorkloadStatus.FAILED) {
    actions.push({
      label: 'Restart',
      icon: 'pi pi-replay',
      command: () => act(() => Kinotic.workloadOrchestration.restartWorkload(item.id), 'Workload restarting', 'Failed to restart workload')
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
      accept: () => act(() => Kinotic.workloadOrchestration.destroyWorkload(item.id), 'Workload destroyed', 'Failed to destroy workload')
    })
  })
  return actions
}

watch(() => props.workloads, () => refreshTable())
</script>
