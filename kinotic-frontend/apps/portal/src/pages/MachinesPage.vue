<template>
  <div class="flex flex-col">
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
        {{ formatDate(item.created) }}
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
          A machine connects to this application's API with the Kinotic client, using the
          client id and secret shown after creation.
        </p>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" outlined @click="createDialogVisible = false" />
        <Button label="Create" :loading="creating" @click="create" />
      </template>
    </Dialog>

    <Dialog v-model:visible="secretDialogVisible" modal :header="secretDialogTitle" :style="{ width: '34rem' }"
            :closable="false">
      <div class="flex flex-col gap-4">
        <p class="text-sm m-0">
          Store the client secret now — <strong>it is shown only this once</strong> and can only be
          replaced, not recovered.
        </p>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Client ID</label>
          <div class="flex items-center gap-2">
            <code class="text-sm break-all grow">{{ secretClientId }}</code>
            <Button icon="pi pi-copy" severity="secondary" text aria-label="Copy client ID"
                    @click="copy(secretClientId, 'Client ID')" />
          </div>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Client secret</label>
          <div class="flex items-center gap-2">
            <code class="text-sm break-all grow">{{ secretValue }}</code>
            <Button icon="pi pi-copy" severity="secondary" text aria-label="Copy client secret"
                    @click="copy(secretValue, 'Client secret')" />
          </div>
        </div>
      </div>
      <template #footer>
        <Button label="Done" @click="closeSecretDialog" />
      </template>
    </Dialog>
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

import { Kinotic } from '@kinotic-ai/core'
import type { MachineParticipantIdentity } from '@kinotic-ai/os-api'

import CrudTable from '@/components/CrudTable.vue'
import { filteredPageLoader, statusSeverity, useCrudTablePage } from '@/components/useCrudTablePage'
import type { CrudHeader } from '@/types/CrudHeader'
import type { DescriptiveIdentifiable } from '@/types/DescriptiveIdentifiable'
import DatetimeUtil from '@/util/DatetimeUtil'
import { showErrorToast } from '@kinotic-ai/frontend-common'

/** One table row — a machine API client of the application. */
interface MachineRow extends DescriptiveIdentifiable {
  id: string
  displayName: string | null
  status: 'Active' | 'Disabled'
  created: number | null
  enabled: boolean
}

/** Machine API clients of one application, managed by the owning org's members. */
const props = defineProps<{
  applicationId: string
}>()

const headers: CrudHeader[] = [
  { field: 'displayName', header: 'Name', sortable: false },
  { field: 'clientId', header: 'Client ID', sortable: false },
  { field: 'status', header: 'Status', sortable: false },
  { field: 'created', header: 'Created', sortable: false }
]

const createDialogVisible = ref(false)
const machineName = ref('')
const creating = ref(false)
const secretDialogVisible = ref(false)
const secretDialogTitle = ref('')
const secretClientId = ref('')
const secretValue = ref('')

const toast = useToast()
const confirm = useConfirm()

// no server-side machine search; an org has few machines, so filtering the page suffices
const { crudTable, tableSearch, dataSource, refreshTable, run } = useCrudTablePage(
  filteredPageLoader(
    pageable => Kinotic.machines.findMachines(props.applicationId, pageable),
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
    const result = await Kinotic.machines.createMachine(name, props.applicationId)
    createDialogVisible.value = false
    showSecret(`Machine created — ${name}`, result.machine.id ?? '', result.clientSecret)
    refreshTable()
  } catch (err) {
    showErrorToast(toast, 'Failed to create machine', err, { life: 8000 })
  } finally {
    creating.value = false
  }
}

function showSecret(title: string, clientId: string, secret: string) {
  secretDialogTitle.value = title
  secretClientId.value = clientId
  secretValue.value = secret
  secretDialogVisible.value = true
}

function closeSecretDialog() {
  secretDialogVisible.value = false
  // the plaintext must not outlive the dialog that discloses it
  secretClientId.value = ''
  secretValue.value = ''
}

async function copy(value: string, what: string) {
  try {
    await navigator.clipboard.writeText(value)
    toast.add({ severity: 'success', summary: `${what} copied`, life: 3000 })
  } catch {
    toast.add({ severity: 'error', summary: `Could not copy ${what.toLowerCase()}`, life: 5000 })
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
        const secret = await Kinotic.machines.rotateSecret(item.id)
        showSecret(`New secret — ${item.displayName || item.id}`, item.id, secret)
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
        () => Kinotic.machines.setMachineEnabled(item.id, !item.enabled),
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
    accept: () => run(() => Kinotic.machines.removeMachine(item.id), 'Machine removed', 'Failed to remove machine')
  })
}

</script>
