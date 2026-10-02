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
      <template #toolbar>
        <slot name="toolbar" />
      </template>

      <template #item.name="{ item }">
        <span class="block max-w-full truncate font-sans text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="item.name">{{ item.name }}</span>
        <span class="mt-0.5 flex max-w-full items-center gap-1.5 truncate text-xs text-muted-color">
          <template v-if="ownerHeader">
            <span v-if="item.owner" class="truncate" v-tooltip.top="item.owner">{{ item.owner }}</span>
            <span v-else>{{ ownerFallback }}</span>
          </template>
          <span v-if="!item.detached" class="shrink-0 rounded bg-surface-100 px-1 font-sans text-[0.625rem] font-medium uppercase tracking-wide text-surface-500 dark:bg-surface-800 dark:text-surface-400">one-off</span>
        </span>
      </template>

      <template #item.status="{ item }">
        <span class="flex flex-col items-start gap-1">
          <Tag :value="item.status" :severity="workloadSeverity(item.status)" />
          <NodeUnreachableNote v-if="item.unreachable" :message="item.unreachable.message" />
        </span>
      </template>

      <template #item.node="{ item }">
        <RouterLink v-if="item.nodeId" :to="`/worker-nodes/${encodeURIComponent(item.nodeId)}`"
                    class="text-surface-600 hover:text-surface-950 hover:underline dark:text-surface-300 dark:hover:text-surface-0" @click.stop>{{ item.node }}</RouterLink>
        <span v-else>—</span>
      </template>

      <template #item.image="{ item }">
        <span class="inline-block max-w-full truncate rounded-md bg-surface-100 px-1.5 py-0.5 text-xs text-surface-600 dark:bg-surface-800 dark:text-surface-300"
              v-tooltip.top="item.image">{{ imageName(item.image) }}</span>
      </template>

      <template #item.resources="{ item }">
        <span class="text-xs text-surface-600 dark:text-surface-300">{{ item.resources }}</span>
      </template>

      <template #item.created="{ item }">
        <TimePill :date="item.created" />
      </template>
    </CrudTable>

    <WorkloadLogsDialog
      v-if="logsWorkload"
      v-model:visible="logsVisible"
      :organization-id="logsWorkload.organizationId"
      :workload-id="logsWorkload.id ?? ''"
      :workload-name="logsWorkload.name"
      :workload="logsWorkload"
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
import { WorkloadStatus, type StatusCondition, type Workload } from '@kinotic-ai/management-api'
import { CrudTable, DatetimeUtil, TimePill, WorkloadLogsDialog, formatMb, pageNumberOf, useCrudTablePage,
         NodeUnreachableNote, type CrudHeader, type DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'

import { formatCpus } from '@/util/nodes'
import { scopePath, type Scope } from '@/util/scope'
import { nodeUnreachable, runOpen, shortImage, workloadSeverity } from '@/util/workloads'

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
  /** The orchestrator's mark that the node has not answered for this workload, or null. */
  unreachable: StatusCondition | null
  nodeId: string | null
  node: string
  owner: string | null
  image: string
  resources: string
  created: number | null
  detached: boolean
}

const DEFAULT_SORT = [new Order('created', Direction.DESC)]

const router = useRouter()
const confirm = useConfirm()

const logsWorkload = ref<Workload | null>(null)
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

/** An image as its name and tag, without the registry or repository path: "workload-runner:latest". */
function imageName(image: string): string {
  return shortImage(image).split('/').pop() ?? image
}

const ownerFallback = computed(() => ownerHeader.value === 'Organization' ? 'platform' : 'organization')

const headers = computed<CrudHeader[]>(() => {
  const ret: CrudHeader[] = [
    { field: 'name', header: 'Name', sortable: true, width: '28%' },
    { field: 'status', header: 'Status', sortable: true, width: '12%' }
  ]
  if (props.showNode) {
    ret.push({ field: 'node', header: 'Node', sortable: false, optional: true, width: '16%' })
  }
  ret.push(
    { field: 'image', header: 'Image', sortable: false, optional: true, width: '16%' },
    { field: 'resources', header: 'Resources', sortable: false, optional: true, width: '15%' },
    { field: 'created', header: 'Created', sortable: true, optional: true, width: '13%' }
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
      cmp = (DatetimeUtil.toEpochMillis(a.created) ?? 0) - (DatetimeUtil.toEpochMillis(b.created) ?? 0)
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
    unreachable: nodeUnreachable(workload) ?? null,
    nodeId: workload.nodeId,
    node: workload.nodeId ? props.nodeNames[workload.nodeId] ?? workload.nodeId : '',
    owner: ownerOf(workload),
    image: workload.image,
    resources: `${formatCpus(workload.cpus)} CPU · ${formatMb(workload.memoryMb)} · ${formatMb(workload.diskSizeMb)}`,
    created: workload.created,
    detached: workload.detached
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
        logsWorkload.value = props.workloads.find(workload => workload.id === item.id) ?? null
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
  // A run holding a VM is destroyed; an ended one is deleted with its logs
  if (runOpen(item.status)) {
    actions.push({
      label: 'Destroy',
      icon: 'pi pi-power-off',
      command: () => confirm.require({
        header: 'Confirm destroy',
        message: `Destroy workload ${item.name}? Its VM and disk are removed permanently; its record and logs stay.`,
        icon: 'pi pi-exclamation-triangle',
        acceptProps: { label: 'Destroy', severity: 'danger' },
        rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
        accept: () => act(() => Kinotic.workloadOrchestration.destroyWorkload(item.id), 'Workload destroyed', 'Failed to destroy workload')
      })
    })
  } else {
    actions.push({
      label: 'Delete',
      icon: 'pi pi-trash',
      command: () => confirm.require({
        header: 'Confirm delete',
        message: `Delete workload ${item.name}? Its record and its logs are removed permanently.`,
        icon: 'pi pi-exclamation-triangle',
        acceptProps: { label: 'Delete', severity: 'danger' },
        rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
        accept: () => act(() => Kinotic.workloadOrchestration.deleteWorkload(item.id), 'Workload deleted', 'Failed to delete workload')
      })
    })
  }
  return actions
}

watch(() => props.workloads, () => refreshTable())
</script>
