<template>
  <div class="flex flex-col">
    <PageHeader title="Machines"
                description="Platform daemons that connect to the system zone with their own client id and secret, such as a worker node's vm-manager." />

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

      <template #item.created="{ item }">
        {{ item.created ? formatDate(item.created) : '—' }}
      </template>
    </CrudTable>

    <Dialog v-model:visible="createDialogVisible" modal header="Create machine" :style="{ width: '28rem' }">
      <div class="flex flex-col gap-4">
        <div class="flex flex-col gap-1">
          <label for="machine-name" class="text-sm font-medium">Name</label>
          <InputText id="machine-name" v-model="machineName" placeholder="dev-node-1 vm-manager, …"
                     autocomplete="off" autofocus @keyup.enter="create" />
        </div>
        <p class="text-sm text-muted-color m-0">
          A machine connects with the Kinotic client, using the client id and secret shown after
          creation — the <code>KINOTIC_CLIENT_ID</code> and <code>KINOTIC_CLIENT_SECRET</code> a
          vm-manager is configured with.
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
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import type { MachineParticipantIdentity } from '@kinotic-ai/management-api'
import {
  CrudTable,
  MachineSecretDialog,
  PageHeader,
  DatetimeUtil,
  filteredPageLoader,
  showErrorToast,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable,
  type MachineSecret
} from '@kinotic-ai/frontend-common'

/** One table row — a platform machine. */
interface MachineRow extends DescriptiveIdentifiable {
  id: string
  displayName: string | null
  created: number | null
}

const headers: CrudHeader[] = [
  { field: 'displayName', header: 'Name', sortable: false, width: '32%' },
  { field: 'clientId', header: 'Client ID', sortable: false, width: '40%' },
  { field: 'created', header: 'Created', sortable: false, width: '28%', optional: true }
]

const createDialogVisible = ref(false)
const machineName = ref('')
const creating = ref(false)
const secret = ref<MachineSecret | null>(null)

const toast = useToast()
const confirm = useConfirm()

// no server-side machine search; the platform has few machines, so filtering the page suffices
const { tableSearch, dataSource, refreshTable, run } = useCrudTablePage(
  filteredPageLoader(
    pageable => Kinotic.systemMembers.findMachines(pageable),
    toRow,
    row => [row.displayName, row.id]
  )
)

const formatDate = DatetimeUtil.formatEpochDate

function toRow(machine: MachineParticipantIdentity): MachineRow {
  return {
    id: machine.id ?? '',
    displayName: machine.displayName,
    created: machine.created
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
    const result = await Kinotic.systemMembers.createMachine(name)
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
    { label: 'Remove machine', icon: 'pi pi-trash', command: () => confirmRemove(item) }
  ]
}

function confirmRemove(item: MachineRow) {
  confirm.require({
    header: 'Remove machine',
    message: `Permanently remove ${item.displayName || item.id}? Its credential is deleted and its client id can never authenticate again.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Remove', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.systemMembers.removeMachine(item.id), 'Machine removed', 'Failed to remove machine')
  })
}
</script>
