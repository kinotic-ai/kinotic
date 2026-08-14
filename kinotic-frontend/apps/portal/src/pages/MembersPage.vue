<template>
  <div class="flex flex-col">
    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      create-new-button-text="Invite member"
      empty-state-text="No members yet"
      :row-actions="rowActions"
      @update:search="tableSearch = $event"
      @add-item="openInviteDialog"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item.status)" />
      </template>

      <template #item.authType="{ item }">
        {{ item.authType || '—' }}
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>

    </CrudTable>

    <Dialog v-model:visible="inviteDialogVisible" modal header="Invite member" :style="{ width: '28rem' }">
      <div class="flex flex-col gap-4">
        <div class="flex flex-col gap-1">
          <label for="invite-email" class="text-sm font-medium">Email</label>
          <InputText id="invite-email" v-model="inviteEmail" type="email" placeholder="person@example.com" autocomplete="off" autofocus />
        </div>
        <div class="flex flex-col gap-1">
          <label for="invite-name" class="text-sm font-medium">Display name (optional)</label>
          <InputText id="invite-name" v-model="inviteDisplayName" placeholder="Their name" autocomplete="off" @keyup.enter="sendInvite" />
        </div>
        <p class="text-sm text-muted-color m-0">
          They'll be able to accept by setting a password{{ providersHint }}.
          Invite the email address they'll sign in with.
        </p>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" outlined @click="inviteDialogVisible = false" />
        <Button label="Send invitation" :loading="inviting" @click="sendInvite" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import {
  FunctionalIterablePage,
  Kinotic,
  Pageable,
  type IterablePage,
  type Page
} from '@kinotic-ai/core'
import type { PendingInviteSummary, UserParticipantIdentity } from '@kinotic-ai/os-api'

import { CrudTable } from '@kinotic-ai/frontend-common'
import { statusSeverity, useCrudTablePage } from '@kinotic-ai/frontend-common'
import type { CrudHeader } from '@kinotic-ai/frontend-common'
import type { DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'
import { KinoticStates } from '@/states'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'
import { apiUrl, showErrorToast } from '@kinotic-ai/frontend-common'
import { createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('members')

/** One table row — a member or, when {@link invite} is true, a pending invitation. */
interface MemberRow extends DescriptiveIdentifiable {
  id: string
  email: string
  displayName: string | null
  status: 'Invited' | 'Active' | 'Disabled'
  authType: string | null
  created: number | null
  enabled?: boolean
  invite?: boolean
}

/**
 * Members of the organization (applicationId null) or of one application. Pending
 * invitations render inline ahead of the members with an Invited badge; searching
 * matches members on the server and pending invitations client-side.
 */
const props = withDefaults(defineProps<{
  applicationId?: string | null
}>(), {
  applicationId: null,
})

const headers: CrudHeader[] = [
  { field: 'email', header: 'Email', sortable: false, width: '30%' },
  { field: 'displayName', header: 'Name', sortable: false, width: '22%' },
  { field: 'status', header: 'Status', sortable: false, width: '14%' },
  { field: 'authType', header: 'Auth type', sortable: false, width: '16%' },
  { field: 'created', header: 'Created', sortable: false, width: '18%' }
]

const inviteDialogVisible = ref(false)
const inviteEmail = ref('')
const inviteDisplayName = ref('')
const inviting = ref(false)
const socialProviderKeys = ref<string[]>([])

const toast = useToast()
const confirm = useConfirm()
const userState = KinoticStates.getUserState()

const { crudTable, tableSearch, dataSource, refreshTable, run } = useCrudTablePage(load)

const formatDate = DatetimeUtil.formatEpochDate

const providersHint = computed<string>(() => {
  if (props.applicationId !== null) {
    return " or signing in with any provider configured for this application"
  }
  if (socialProviderKeys.value.length === 0) {
    return ''
  }
  const names = socialProviderKeys.value.map(key => providerDisplayName(key))
  return ' or signing in with ' + names.join(', ')
})

onMounted(async () => {
  // Same source the login page uses for its social buttons; org invitees are offered these.
  if (props.applicationId === null) {
    try {
      const res = await fetch(apiUrl('/api/auth/org/login/providers'), { credentials: 'same-origin' })
      if (res.ok) {
        const data = await res.json()
        if (Array.isArray(data)) socialProviderKeys.value = data
      }
    } catch (err) {
      debug('Failed to load providers: %O', err)
    }
  }
})

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const membersPage = searchText
    ? await Kinotic.members.searchMembers(searchText, props.applicationId, pageable)
    : await Kinotic.members.findMembers(props.applicationId, pageable)

  const invites = await Kinotic.members.findPendingInvites(props.applicationId, Pageable.create(0, 100, null))
  let inviteRows = (invites.content ?? []).map(invite => toInviteRow(invite))
  let inviteTotal = invites.totalElements ?? inviteRows.length
  if (searchText) {
    // Invite search is client-side over the fetched page — there's no server-side
    // invite search, and hiding a just-invited address behind a filter is worse.
    const needle = searchText.trim().toLowerCase()
    inviteRows = inviteRows.filter(row =>
      row.email.toLowerCase().includes(needle) ||
      (row.displayName ?? '').toLowerCase().includes(needle))
    inviteTotal = inviteRows.length
  }
  // Prepended on the first page only, so deeper pages stay pure member pages.
  if (pageNumberOf(pageable) !== 0) {
    inviteRows = []
  }

  const page: Page<DescriptiveIdentifiable> = {
    content: [...inviteRows, ...(membersPage.content ?? []).map(user => toMemberRow(user))],
    totalElements: (membersPage.totalElements ?? 0) + inviteTotal,
    cursor: undefined
  }

  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

function pageNumberOf(pageable: Pageable): number {
  return (pageable as Pageable & { pageNumber?: number }).pageNumber ?? 0
}

function toInviteRow(invite: PendingInviteSummary): MemberRow {
  return {
    id: invite.id ?? '',
    email: invite.email,
    displayName: invite.displayName,
    status: 'Invited',
    authType: null,
    created: invite.created,
    invite: true
  }
}

function toMemberRow(user: UserParticipantIdentity): MemberRow {
  return {
    id: user.id ?? '',
    email: user.email,
    displayName: user.displayName,
    status: user.enabled ? 'Active' : 'Disabled',
    authType: user.authType,
    created: user.created,
    enabled: user.enabled
  }
}

/** The server rejects self-disable/remove; hiding the buttons mirrors that rule. */
function isSelf(item: MemberRow): boolean {
  return item.id === userState.connectedInfo?.participant?.id
}

function providerDisplayName(key: string): string {
  switch (key) {
    case 'google':   return 'Google'
    case 'azure-ad': return 'Microsoft'
    case 'github':   return 'GitHub'
    default:         return key.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
  }
}

function openInviteDialog() {
  inviteEmail.value = ''
  inviteDisplayName.value = ''
  inviteDialogVisible.value = true
}

async function sendInvite() {
  const email = inviteEmail.value.trim()
  if (!email || !email.includes('@')) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Please enter a valid email address', life: 5000 })
    return
  }
  inviting.value = true
  try {
    await Kinotic.members.inviteMember(email, inviteDisplayName.value.trim() || null, props.applicationId)
    inviteDialogVisible.value = false
    toast.add({ severity: 'success', summary: 'Invitation sent', detail: `Invited ${email}`, life: 5000 })
    // A leftover filter would hide the new Invited row; clearing it reloads the
    // table through CrudTable's search-prop watch, so refresh only when empty.
    if (tableSearch.value) {
      tableSearch.value = ''
    } else {
      refreshTable()
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to send invitation', err, { life: 8000 })
  } finally {
    inviting.value = false
  }
}

function rowActions(item: MemberRow): MenuItem[] {
  if (item.invite) {
    return [
      { label: 'Cancel invitation', icon: 'pi pi-times', command: () => confirmCancelInvite(item) }
    ]
  }
  if (isSelf(item)) {
    return []
  }
  return [
    {
      label: item.enabled ? 'Disable member' : 'Enable member',
      icon: item.enabled ? 'pi pi-ban' : 'pi pi-check-circle',
      command: () => confirmToggleEnabled(item)
    },
    { label: 'Remove member', icon: 'pi pi-trash', command: () => confirmRemove(item) }
  ]
}

function confirmCancelInvite(item: MemberRow) {
  confirm.require({
    header: 'Cancel invitation',
    message: `Cancel the invitation for ${item.email}? Their accept link stops working.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Cancel invitation', severity: 'danger' },
    rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.members.cancelInvite(item.id), 'Invitation cancelled', 'Failed to cancel invitation')
  })
}

function confirmToggleEnabled(item: MemberRow) {
  const disabling = item.enabled === true
  confirm.require({
    header: disabling ? 'Disable member' : 'Enable member',
    message: disabling
      ? `Disable ${item.email}? They can no longer sign in; sessions already open last until they expire.`
      : `Enable ${item.email}? They can sign in again.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: disabling ? 'Disable' : 'Enable', severity: disabling ? 'danger' : 'primary' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(
        () => Kinotic.members.setMemberEnabled(item.id, !item.enabled),
        disabling ? 'Member disabled' : 'Member enabled',
        'Failed to update member')
  })
}

function confirmRemove(item: MemberRow) {
  confirm.require({
    header: 'Remove member',
    message: `Permanently remove ${item.email}? Their sign-in and stored credential are deleted.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Remove', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.members.removeMember(item.id), 'Member removed', 'Failed to remove member')
  })
}

</script>
