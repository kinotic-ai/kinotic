<template>
  <div class="flex flex-col">
    <PageHeader title="Machines" :description="description" />

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      create-new-button-text="Create machine"
      empty-state-text="No machines yet"
      :row-actions="rowActions"
      @update:search="tableSearch = $event"
      @add-item="openCreateDialog"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.clientId="{ item }">
        <span class="font-mono text-sm">{{ item.id }}</span>
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item.status)" />
      </template>

      <template #item.created="{ item }">
        {{ item.created ? formatDate(item.created) : '—' }}
      </template>
    </CrudTable>

    <Dialog v-model:visible="createDialogVisible" modal header="Create machine" :style="{ width: '28rem' }">
      <div class="flex flex-col gap-4">
        <div class="flex flex-col gap-1">
          <label for="machine-name" class="text-sm font-medium">Name</label>
          <InputText id="machine-name" v-model="machineName" placeholder="vm-manager, billing-sync, …"
                     autocomplete="off" autofocus @keyup.enter="create" />
        </div>
        <p class="text-sm text-muted-color m-0">
          <slot name="create-hint" />
        </p>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" outlined @click="createDialogVisible = false" />
        <Button label="Create" :loading="creating" @click="create" />
      </template>
    </Dialog>

    <MachineSecretDialog v-model="secret" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import type { Page, Pageable } from '@kinotic-ai/core'
import type { MachineParticipantIdentity, MachineProvisionResult } from '@kinotic-ai/management-api'

import CrudTable from './CrudTable.vue'
import MachineSecretDialog, { type MachineSecret } from './MachineSecretDialog.vue'
import PageHeader from './PageHeader.vue'
import { filteredPageLoader, statusSeverity, useCrudTablePage } from './useCrudTablePage'
import type { CrudHeader } from '../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../types/DescriptiveIdentifiable'
import DatetimeUtil from '../util/DatetimeUtil'
import { showErrorToast } from '../util/helpers'

/**
 * What a machine listing can do, bound to the scope whose machines it manages — the
 * application for an organization's API clients, the platform for its own daemons.
 */
export interface MachineOperations {
  findMachines(pageable: Pageable): Promise<Page<MachineParticipantIdentity>>
  createMachine(displayName: string): Promise<MachineProvisionResult>
  rotateSecret(machineId: string): Promise<string>
  setMachineEnabled(machineId: string, enabled: boolean): Promise<void>
  removeMachine(machineId: string): Promise<void>
}

/** One table row — a machine of the listed scope. */
interface MachineRow extends DescriptiveIdentifiable {
  id: string
  displayName: string | null
  status: 'Active' | 'Disabled'
  created: number | null
  enabled: boolean
}

/**
 * The machines of one scope, with the whole lifecycle its members may drive: create (disclosing
 * the generated secret once), rotate that secret, disable and enable, and remove. The
 * {@code create-hint} slot fills the sentence under the name field in the create dialog, where
 * each scope says what its machines connect to.
 */
const props = defineProps<{
  description: string
  machines: MachineOperations
}>()

const headers: CrudHeader[] = [
  { field: 'displayName', header: 'Name', sortable: false, width: '26%' },
  { field: 'clientId', header: 'Client ID', sortable: false, width: '34%', optional: true },
  { field: 'status', header: 'Status', sortable: false, width: '16%' },
  { field: 'created', header: 'Created', sortable: false, width: '24%', optional: true }
]

const createDialogVisible = ref(false)
const machineName = ref('')
const creating = ref(false)
const secret = ref<MachineSecret | null>(null)

const toast = useToast()
const confirm = useConfirm()

// no server-side machine search; a scope has few machines, so filtering the page suffices
const { tableSearch, dataSource, refreshTable, run } = useCrudTablePage(
  filteredPageLoader(
    pageable => props.machines.findMachines(pageable),
    toRow,
    row => [row.displayName, row.id]
  )
)

const formatDate = DatetimeUtil.formatEpochDate

function toRow(machine: MachineParticipantIdentity): MachineRow {
  return {
    id: machine.id ?? '',
    displayName: machine.displayName,
    status: machine.enabled ? 'Active' : 'Disabled',
    created: machine.created,
    enabled: machine.enabled
  }
}

function openCreateDialog() {
  machineName.value = ''
  createDialogVisible.value = true
}

async function create() {
  const name = machineName.value.trim()
  if (!name) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Please enter a machine name', life: 5000 })
    return
  }
  creating.value = true
  try {
    const result = await props.machines.createMachine(name)
    createDialogVisible.value = false
    secret.value = {
      title: `Machine created — ${name}`,
      clientId: result.machine.id ?? '',
      clientSecret: result.clientSecret
    }
    refreshTable()
  } catch (err) {
    showErrorToast(toast, 'Failed to create machine', err, { life: 8000 })
  } finally {
    creating.value = false
  }
}

function rowActions(item: MachineRow): MenuItem[] {
  return [
    { label: 'Rotate secret', icon: 'pi pi-refresh', command: () => confirmRotate(item) },
    {
      label: item.enabled ? 'Disable machine' : 'Enable machine',
      icon: item.enabled ? 'pi pi-ban' : 'pi pi-check-circle',
      command: () => confirmToggleEnabled(item)
    },
    { label: 'Remove machine', icon: 'pi pi-trash', command: () => confirmRemove(item) }
  ]
}

function confirmRotate(item: MachineRow) {
  confirm.require({
    header: 'Rotate secret',
    message: `Rotate the secret for ${item.displayName || item.id}? The current secret stops working immediately.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Rotate', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        const clientSecret = await props.machines.rotateSecret(item.id)
        secret.value = { title: `New secret — ${item.displayName || item.id}`, clientId: item.id, clientSecret }
      } catch (err) {
        showErrorToast(toast, 'Failed to rotate secret', err, { life: 8000 })
      }
    }
  })
}

function confirmToggleEnabled(item: MachineRow) {
  const disabling = item.enabled
  confirm.require({
    header: disabling ? 'Disable machine' : 'Enable machine',
    message: disabling
      ? `Disable ${item.displayName || item.id}? It is cut off on its next request, unexpired tokens included.`
      : `Enable ${item.displayName || item.id}? It can authenticate again with its existing secret.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: disabling ? 'Disable' : 'Enable', severity: disabling ? 'danger' : 'primary' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(
        () => props.machines.setMachineEnabled(item.id, !item.enabled),
        disabling ? 'Machine disabled' : 'Machine enabled',
        'Failed to update machine')
  })
}

function confirmRemove(item: MachineRow) {
  confirm.require({
    header: 'Remove machine',
    message: `Permanently remove ${item.displayName || item.id}? Its credential is deleted and its client id can never authenticate again.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Remove', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => props.machines.removeMachine(item.id), 'Machine removed', 'Failed to remove machine')
  })
}
</script>
