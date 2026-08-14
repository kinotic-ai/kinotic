<template>
  <div class="flex flex-col">
    <PageHeader title="Connected apps"
                description="Clients you have authorized to act on your behalf. Revoking one signs it out everywhere." />

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :is-show-add-new="false"
      :search="tableSearch"
      empty-state-text="Nothing has been authorized on your behalf"
      :row-actions="rowActions"
      @update:search="tableSearch = $event"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.kind="{ item }">
        {{ item.kind }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item.status)" />
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>
    </CrudTable>

    <Dialog v-model:visible="sessionsDialogVisible" modal :header="sessionsDialogTitle" :style="{ width: '34rem' }">
      <p v-if="sessions.length === 0" class="text-sm text-muted-color m-0">No live sessions.</p>
      <div v-else class="flex flex-col gap-3">
        <div v-for="session in sessions" :key="session.familyId"
             class="flex items-center justify-between gap-4 border rounded-md p-3 border-surface-200 dark:border-surface-700">
          <div class="flex flex-col gap-1">
            <span class="font-medium">{{ session.label || 'Unnamed session' }}</span>
            <span class="text-sm text-muted-color">
              Last active {{ formatDateTime(session.lastRefreshedAt) }} · expires {{ formatDate(session.expiresAt) }}
            </span>
          </div>
          <Button label="End session" severity="danger" outlined size="small"
                  :loading="endingSession === session.familyId"
                  @click="endSession(session)" />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { DelegateKind } from '@kinotic-ai/os-api'
import type { DelegateSession, DelegatingParticipantIdentity } from '@kinotic-ai/os-api'

import { CrudTable } from '@kinotic-ai/frontend-common'
import { filteredPageLoader, statusSeverity, useCrudTablePage } from '@kinotic-ai/frontend-common'
import type { CrudHeader } from '@kinotic-ai/frontend-common'
import type { DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import PageHeader from '@/components/PageHeader.vue'

/** One table row — a client authorized to act on the signed-in user's behalf. */
interface DelegateRow extends DescriptiveIdentifiable {
  id: string
  displayName: string | null
  kind: string
  status: 'Active' | 'Revoked'
  created: number | null
  enabled: boolean
}

const headers: CrudHeader[] = [
  { field: 'displayName', header: 'Name', sortable: false, width: '30%' },
  { field: 'kind', header: 'Kind', sortable: false, width: '22%' },
  { field: 'status', header: 'Status', sortable: false, width: '18%' },
  { field: 'created', header: 'First authorized', sortable: false, width: '30%' }
]

const sessionsDialogVisible = ref(false)
const sessionsDialogTitle = ref('')
const sessions = ref<DelegateSession[]>([])
const sessionsDelegateId = ref('')
const endingSession = ref<string | null>(null)

const toast = useToast()
const confirm = useConfirm()

// no server-side delegate search; a user has few delegates, so filtering the page suffices
const { crudTable, tableSearch, dataSource, refreshTable } = useCrudTablePage(
  filteredPageLoader(
    pageable => Kinotic.delegates.findMyDelegates(pageable),
    toRow,
    row => [row.displayName, row.kind]
  )
)

const formatDate = DatetimeUtil.formatEpochDate
const formatDateTime = DatetimeUtil.formatEpochDateTime

function toRow(delegate: DelegatingParticipantIdentity): DelegateRow {
  return {
    id: delegate.id ?? '',
    displayName: delegate.displayName,
    kind: kindLabel(delegate.delegateKind),
    status: delegate.enabled ? 'Active' : 'Revoked',
    created: delegate.created,
    enabled: delegate.enabled
  }
}

function kindLabel(kind: DelegateKind | null): string {
  switch (kind) {
    case DelegateKind.CLI:        return 'Kinotic CLI'
    case DelegateKind.MCP_CLIENT: return 'MCP client'
    default:                      return '—'
  }
}

function rowActions(item: DelegateRow): MenuItem[] {
  const actions: MenuItem[] = [
    { label: 'View sessions', icon: 'pi pi-list', command: () => openSessions(item) }
  ]
  if (item.enabled) {
    actions.push({ label: 'Revoke access', icon: 'pi pi-ban', command: () => confirmRevoke(item) })
  }
  return actions
}

async function openSessions(item: DelegateRow) {
  sessionsDelegateId.value = item.id
  sessionsDialogTitle.value = `Sessions — ${item.displayName || item.kind}`
  try {
    sessions.value = await Kinotic.delegates.findSessions(item.id)
    sessionsDialogVisible.value = true
  } catch (err) {
    showErrorToast(toast, 'Failed to load sessions', err, { life: 8000 })
  }
}

async function endSession(session: DelegateSession) {
  endingSession.value = session.familyId
  try {
    await Kinotic.delegates.revokeSession(sessionsDelegateId.value, session.familyId)
    sessions.value = sessions.value.filter(s => s.familyId !== session.familyId)
    toast.add({ severity: 'success', summary: 'Session ended', life: 5000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to end session', err, { life: 8000 })
  } finally {
    endingSession.value = null
  }
}

function confirmRevoke(item: DelegateRow) {
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${item.displayName || item.kind}? It loses access on its next request; approving it again restores access.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.delegates.revokeDelegate(item.id)
        toast.add({ severity: 'success', summary: 'Access revoked', detail: item.displayName ?? undefined, life: 5000 })
        refreshTable()
      } catch (err) {
        showErrorToast(toast, 'Failed to revoke access', err, { life: 8000 })
      }
    }
  })
}
</script>
