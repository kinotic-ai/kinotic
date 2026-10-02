<template>
  <!-- the machines at a glance, from the server's own total -->
  <section v-if="machineTotal !== null"
           class="mb-4 flex items-center gap-3 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30">
    <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', tint]">
      <ServerCog :size="18" :stroke-width="1.75" aria-hidden="true" />
    </span>
    <div class="leading-tight">
      <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Machines</div>
      <div class="mt-0.5 text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ machineTotal }}</div>
    </div>
  </section>

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
      <span class="flex min-w-0 items-center gap-2.5">
        <span :class="['flex h-7 w-7 shrink-0 items-center justify-center rounded-md', TINTS.ink]">
          <ServerCog :size="14" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <span class="truncate font-sans text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="item.displayName">
          {{ item.displayName || '—' }}
        </span>
      </span>
    </template>

    <template #item.clientId="{ item }">
      <span class="inline-flex min-w-0 max-w-full items-center gap-1">
        <span class="truncate rounded-md bg-surface-100 px-1.5 py-0.5 text-xs text-surface-700 dark:bg-surface-800 dark:text-surface-200"
              v-tooltip.top="item.id">{{ item.id }}</span>
        <!-- the client id goes into a machine's configuration, so it copies in one click -->
        <button type="button"
                class="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-surface-400 transition-colors hover:bg-surface-100 hover:text-surface-900 dark:hover:bg-surface-800 dark:hover:text-surface-0"
                :aria-label="copiedId === item.id ? 'Copied' : 'Copy client ID'" v-tooltip.top="copiedId === item.id ? 'Copied' : 'Copy client ID'"
                @click.stop="copyClientId(item.id)">
          <component :is="copiedId === item.id ? Check : Copy" :size="13" :stroke-width="1.75" aria-hidden="true" />
        </button>
      </span>
    </template>

    <template #item.status="{ item }">
      <Tag :value="item.status" :severity="statusSeverity(item.status)" />
    </template>

    <template #item.created="{ item }">
      <TimePill :date="item.created" />
    </template>
  </CrudTable>

  <FormDialog v-model:visible="createDialogVisible" :icon="ServerCog" title="Create machine"
              description="A machine is a client that connects to Kinotic on its own, without a person signing in."
              @submit="create">
    <div>
      <label for="machine-name" class="mb-2 block text-sm font-medium">Name</label>
      <IconField>
        <InputIcon><TagIcon :size="16" :stroke-width="1.75" aria-hidden="true" /></InputIcon>
        <InputText id="machine-name" v-model="machineName" placeholder="vm-manager, billing-sync, …"
                   autocomplete="off" autofocus class="w-full" />
      </IconField>
      <p class="mt-1.5 text-[0.8125rem] text-muted-color">Name it for what it runs as, so it reads clearly in the list.</p>
    </div>

    <div class="rounded-xl border border-surface-200 bg-surface-0 px-4 pt-3.5 pb-1 dark:border-surface-700 dark:bg-surface-900">
      <p class="mb-2.5 text-sm font-semibold text-surface-950 dark:text-surface-0">What happens next</p>
      <ol>
        <li v-for="(step, position) in NEXT_STEPS" :key="step.title"
            class="flex items-start gap-3 border-t border-surface-100 py-2.5 dark:border-surface-800">
          <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-surface-100 text-[0.6875rem] font-semibold tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ position + 1 }}</span>
          <div class="min-w-0">
            <p class="text-sm text-surface-800 dark:text-surface-100">{{ step.title }}</p>
            <p class="mt-0.5 text-[0.8125rem] text-muted-color">{{ step.detail }}</p>
          </div>
        </li>
      </ol>
    </div>

    <p v-if="$slots['create-hint']" class="-mt-2 text-[0.8125rem] leading-relaxed text-muted-color">
      <slot name="create-hint" />
    </p>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="createDialogVisible = false" />
      <Button type="submit" label="Create machine" :loading="creating" :disabled="machineName.trim() === ''" />
    </template>
  </FormDialog>

  <MachineSecretDialog v-model="secret" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import { Check, Copy, ServerCog, Tag as TagIcon } from '@lucide/vue'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import type { Page, Pageable } from '@kinotic-ai/core'
import type { MachineParticipantIdentity, MachineProvisionResult } from '@kinotic-ai/management-api'

import CrudTable from './CrudTable.vue'
import FormDialog from './FormDialog.vue'
import MachineSecretDialog, { type MachineSecret } from './MachineSecretDialog.vue'
import { filteredPageLoader, statusSeverity, useCrudTablePage } from './useCrudTablePage'
import type { CrudHeader } from '../types/CrudHeader'
import type { DescriptiveIdentifiable } from '../types/DescriptiveIdentifiable'
import TimePill from './TimePill.vue'
import { TINTS } from '../util/tints'
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
const props = withDefaults(defineProps<{
  machines: MachineOperations
  /** The icon tint of the scope the machines belong to, one of TINTS. */
  tint?: string
}>(), {
  tint: TINTS.ink
})

/** How many machines the scope has, as the server counts them; null until the first page loads. */
const machineTotal = ref<number | null>(null)

const copiedId = ref<string | null>(null)

async function copyClientId(id: string): Promise<void> {
  await navigator.clipboard.writeText(id)
  copiedId.value = id
  setTimeout(() => { if (copiedId.value === id) copiedId.value = null }, 2000)
}

const headers: CrudHeader[] = [
  { field: 'displayName', header: 'Name', sortable: false, width: '26%' },
  { field: 'clientId', header: 'Client ID', sortable: false, width: '34%', optional: true },
  { field: 'status', header: 'Status', sortable: false, width: '16%' },
  { field: 'created', header: 'Created', sortable: false, width: '24%', optional: true }
]

/** What creating a machine leads to, shown in the create dialog. */
const NEXT_STEPS = [
  { title: 'Kinotic issues its credentials', detail: 'A client ID and a client secret the machine connects with.' },
  { title: 'Copy the secret right away', detail: 'It is shown once. A lost secret can only be rotated, never shown again.' }
]

const createDialogVisible = ref(false)
const machineName = ref('')
const creating = ref(false)
const secret = ref<MachineSecret | null>(null)

const toast = useToast()
const confirm = useConfirm()

// no server-side machine search; a scope has few machines, so filtering the page suffices
const { tableSearch, dataSource, refreshTable, run, removeRow } = useCrudTablePage(
  filteredPageLoader(
    pageable => props.machines.findMachines(pageable).then(page => {
      machineTotal.value = page.totalElements ?? page.content?.length ?? 0
      return page
    }),
    toRow,
    row => [row.displayName, row.id]
  )
)


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
    accept: () => run(async () => {
      await props.machines.removeMachine(item.id)
      removeRow(item.id)
    }, 'Machine removed', 'Failed to remove machine')
  })
}
</script>
