<template>
  <div class="flex flex-col">
    <PageHeader :title="title" :description="membersDescription" />

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :create-new-button-text="inviteLabel"
      empty-state-text="No members yet"
      :row-actions="rowActions"
      :selected-id="highlightedMember?.id"
      @update:search="tableSearch = $event"
      @row-hover="row => hoverMember(row as MemberRow)"
      @on-row-click="row => openMember(row as MemberRow)"
      @add-item="openInviteDialog"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item.status)" />
      </template>

      <template #item.authType="{ item }">
        <TableChip v-if="item.authType" :icon="item.authType === 'OIDC' ? ShieldCheck : KeyRound">{{ authTypeLabel(item.authType) }}</TableChip>
        <span v-else class="text-muted-color">—</span>
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>

    </CrudTable>

    <!-- The picked person opens beside the list; the arrows step through the rows the table shows -->
    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownMembers.length" @step="stepMember">
      <template #title>
        <span v-if="selectedMember" class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedMember.displayName || selectedMember.email }}</span>
      </template>
      <MemberDetail v-if="selectedMember" :member="selectedMember" :tint="memberTint" :index="position - 1" />
    </SteppingDrawer>

    <FormDialog v-model:visible="inviteDialogVisible" :icon="UserPlus" :title="inviteLabel" :description="inviteDescription"
                @submit="sendInvite">
        <div class="flex flex-col gap-5">
          <div>
            <label for="invite-email" class="mb-2 block text-sm font-medium">Email</label>
            <IconField>
              <InputIcon><Mail :size="16" :stroke-width="1.75" aria-hidden="true" /></InputIcon>
              <InputText id="invite-email" v-model="inviteEmail" type="email" placeholder="person@example.com" autocomplete="off" autofocus class="w-full" />
            </IconField>
            <p class="mt-1.5 text-[0.8125rem] text-muted-color">Use the address they'll sign in with.</p>
          </div>
          <div>
            <label for="invite-name" class="mb-2 flex items-center justify-between text-sm font-medium">
              Display name
              <span class="text-xs font-normal text-muted-color">Optional</span>
            </label>
            <IconField>
              <InputIcon><UserRound :size="16" :stroke-width="1.75" aria-hidden="true" /></InputIcon>
              <InputText id="invite-name" v-model="inviteDisplayName" placeholder="Their name" autocomplete="off" class="w-full" />
            </IconField>
          </div>
        </div>

        <div class="rounded-xl border border-surface-200 bg-surface-0 px-4 pt-3.5 pb-1 dark:border-surface-700 dark:bg-surface-900">
          <p class="mb-2.5 text-sm font-semibold text-surface-950 dark:text-surface-0">They can accept by</p>
          <ul>
            <li v-for="method in signInMethods" :key="method.name"
                class="flex items-center gap-3 border-t border-surface-100 py-2.5 dark:border-surface-800">
              <img v-if="method.logo" :src="method.logo" alt="" class="h-5 w-5 shrink-0" />
              <component :is="method.icon" v-else :size="20" :stroke-width="1.75" class="shrink-0 text-surface-600 dark:text-surface-300" aria-hidden="true" />
              <span class="flex-1 text-sm text-surface-800 dark:text-surface-100">{{ method.name }}</span>
              <Check :size="16" :stroke-width="2" class="shrink-0 text-emerald-500" aria-hidden="true" />
            </li>
          </ul>
        </div>

        <template #footer>
          <Button type="button" label="Cancel" severity="secondary" outlined @click="inviteDialogVisible = false" />
          <Button type="submit" label="Send invitation" icon="pi pi-send" :loading="inviting" :disabled="inviteEmail.trim() === ''" />
        </template>
    </FormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Check, Globe, KeyRound, Mail, ShieldCheck, UserPlus, UserRound } from '@lucide/vue'
import githubLogo from '@/assets/github-icon.svg'
import googleLogo from '@/assets/google-icon.svg'
import microsoftLogo from '@/assets/microsoft_online-icon.svg'

import {
  FunctionalIterablePage,
  Kinotic,
  Pageable,
  type IterablePage,
  type Page
} from '@kinotic-ai/core'
import type { PendingInviteSummary, UserParticipantIdentity } from '@kinotic-ai/management-api'

import { CrudTable, FormDialog, MemberDetail, SteppingDrawer, TableChip, TINTS, authTypeLabel, useSteppingDrawer,
         type MemberSummary } from '@kinotic-ai/frontend-common'
import { PageHeader } from '@kinotic-ai/frontend-common'
import { statusSeverity, useCrudTablePage } from '@kinotic-ai/frontend-common'
import type { CrudHeader } from '@kinotic-ai/frontend-common'
import type { DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'
import { KinoticStates } from '@/states'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'
import { apiUrl, showErrorToast } from '@kinotic-ai/frontend-common'
import { createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('members')

/** One table row — a member or, when {@link invite} is true, a pending invitation. */
interface MemberRow extends MemberSummary {
  enabled?: boolean
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
  { field: 'displayName', header: 'Name', sortable: false, width: '22%', optional: true },
  { field: 'status', header: 'Status', sortable: false, width: '14%' },
  { field: 'authType', header: 'Auth type', sortable: false, width: '16%', optional: true },
  { field: 'created', header: 'Created', sortable: false, width: '18%', optional: true }
]

/** One way an invitee can accept, with the provider's logo or, for the rest, an icon. */
interface SignInMethod {
  name: string
  logo?: string
  icon?: Component
}

const PROVIDER_BRANDS: Record<string, { name: string, logo: string }> = {
  google: { name: 'Google', logo: googleLogo },
  'azure-ad': { name: 'Microsoft', logo: microsoftLogo },
  github: { name: 'GitHub', logo: githubLogo }
}

const inviteDialogVisible = ref(false)
const inviteEmail = ref('')
const inviteDisplayName = ref('')
const inviting = ref(false)
const socialProviderKeys = ref<string[]>([])

const toast = useToast()
const confirm = useConfirm()
const userState = KinoticStates.getUserState()

const {tableSearch, dataSource, refreshTable, run, shownRows, removeRow } = useCrudTablePage(load)

// An organization's members sit in its green; an application's users in the application's blue
const memberTint = computed(() => props.applicationId !== null ? TINTS.blue : TINTS.green)
const shownMembers = computed(() => shownRows.value as MemberRow[])
const { selected: selectedMember, visible: drawerVisible, position, open: openMember, step: stepMember,
        highlighted: highlightedMember, hover: hoverMember } = useSteppingDrawer(shownMembers, member => member.id)

const formatDate = DatetimeUtil.formatEpochDate

// An application's members are its users, which tells them apart from the organization's members
const title = computed<string>(() => props.applicationId !== null ? 'Users' : 'Members')
const inviteLabel = computed<string>(() => props.applicationId !== null ? 'Invite user' : 'Invite member')

const membersDescription = computed<string>(() => {
  return props.applicationId !== null
      ? 'Everyone who signs in to this application, including pending invitations.'
      : 'Everyone in your organization, including pending invitations.'
})

const inviteDescription = computed<string>(() => {
  return props.applicationId !== null
      ? 'Send an invitation to sign in to this application.'
      : 'Send an invitation to join your organization.'
})

// The ways an invitee can accept, one row each in the invite dialog
const signInMethods = computed<SignInMethod[]>(() => {
  const password: SignInMethod = { name: 'Setting a password', icon: markRaw(KeyRound) }
  return props.applicationId !== null
      ? [password, { name: 'Any provider configured for this application', icon: markRaw(ShieldCheck) }]
      : [password, ...socialProviderKeys.value.map(providerMethod)]
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

function providerMethod(key: string): SignInMethod {
  const brand = PROVIDER_BRANDS[key]
  return brand
      ? { name: `Signing in with ${brand.name}`, logo: brand.logo }
      : { name: `Signing in with ${key.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')}`, icon: markRaw(Globe) }
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
    accept: () => run(async () => {
      await Kinotic.members.cancelInvite(item.id)
      removeRow(item.id)
    }, 'Invitation cancelled', 'Failed to cancel invitation')
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
    accept: () => run(async () => {
      await Kinotic.members.removeMember(item.id)
      removeRow(item.id)
    }, 'Member removed', 'Failed to remove member')
  })
}

</script>
