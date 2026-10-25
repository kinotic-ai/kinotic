<template>
  <div class="flex flex-col">
    <PageHeader :title="title" :description="membersDescription" />

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :create-new-button-text="addLabel"
      empty-state-text="No members yet"
      :row-actions="rowActions"
      :selected-id="highlightedMember?.id"
      @update:search="tableSearch = $event"
      @row-hover="row => hoverMember(row as MemberRow)"
      @on-row-click="row => openMember(row as MemberRow)"
      @add-item="openMemberForm"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item.status)" />
      </template>

      <template #item.created="{ item }">
        <TimePill :date="item.created" />
      </template>

    </CrudTable>

    <!-- The picked person opens beside the list; the arrows step through the rows the table shows -->
    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownMembers.length" @step="stepMember">
      <template #title>
        <span v-if="selectedMember" class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedMember.displayName || selectedMember.email }}</span>
      </template>
      <MemberDetail v-if="selectedMember" :member="selectedMember" :tint="memberTint" :index="position - 1" />
    </SteppingDrawer>

    <FormDrawer :visible="memberFormVisible" :title="addLabel" :description="addDescription" @close="memberFormVisible = false">
      <form id="member-form" @submit.prevent="submitMember">
        <div class="flex flex-col gap-5">
          <div>
            <label for="member-email" class="mb-2 block text-sm font-medium">Email</label>
            <IconField>
              <InputIcon><Mail :size="16" :stroke-width="1.75" aria-hidden="true" /></InputIcon>
              <InputText id="member-email" v-model="memberEmail" type="email" placeholder="person@example.com" autocomplete="off" autofocus class="w-full" />
            </IconField>
            <p class="mt-1.5 text-[0.8125rem] text-muted-color">Use the address they'll sign in with.</p>
          </div>
          <div>
            <label for="member-name" class="mb-2 flex items-center justify-between text-sm font-medium">
              Display name
              <span class="text-xs font-normal text-muted-color">Optional</span>
            </label>
            <IconField>
              <InputIcon><UserRound :size="16" :stroke-width="1.75" aria-hidden="true" /></InputIcon>
              <InputText id="member-name" v-model="memberDisplayName" placeholder="Their name" autocomplete="off" class="w-full" />
            </IconField>
          </div>
          <!-- An organization's member is added outright; the email only decides how they first sign in -->
          <div v-if="props.applicationId === null"
               class="flex items-start gap-3 rounded-xl border border-surface-200 bg-surface-0 px-4 py-3.5 dark:border-surface-700 dark:bg-surface-900">
            <ToggleSwitch inputId="member-send-invitation" v-model="sendInvitation" class="mt-0.5 shrink-0" />
            <div class="flex flex-col gap-1">
              <label for="member-send-invitation" class="text-sm font-medium text-surface-950 dark:text-surface-0">Send an invitation email</label>
              <p class="text-[0.8125rem] text-muted-color">
                {{ sendInvitation
                    ? 'They accept it to sign in for the first time, with a password or one of the sign-in methods below.'
                    : 'They sign in through one of your organization\'s own sign-in providers, which admits them the first time.' }}
                People who already sign in to Kinotic organizations always receive an invitation, since joining needs their acceptance.
              </p>
            </div>
          </div>
          <div v-if="props.applicationId !== null || sendInvitation"
               class="rounded-xl border border-surface-200 bg-surface-0 px-4 pt-3.5 pb-1 dark:border-surface-700 dark:bg-surface-900">
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
        </div>
      </form>

      <template #footer>
        <Button type="button" label="Cancel" severity="secondary" outlined @click="memberFormVisible = false" />
        <Button type="submit" form="member-form" :label="props.applicationId !== null ? 'Send invitation' : 'Add member'"
                :icon="props.applicationId !== null ? 'pi pi-send' : 'pi pi-user-plus'" :loading="saving" :disabled="memberEmail.trim() === ''" />
      </template>
    </FormDrawer>

    <Dialog :visible="invitationLink !== null" modal header="Send this invitation link" :style="{ width: '34rem' }"
            @update:visible="visible => { if (!visible) invitationLink = null }">
      <div v-if="invitationLink" class="flex flex-col gap-4">
        <p class="text-sm text-surface-700 dark:text-surface-200">
          This demonstration server can't send email, so no invitation reached {{ invitationLink.email }}.
          Send them this link to accept the invitation, or open it yourself to try it.
        </p>
        <CopyableUrl label="Invitation link" :url="invitationLink.url" />
      </div>
      <template #footer>
        <Button type="button" label="Done" @click="invitationLink = null" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import ToggleSwitch from 'primevue/toggleswitch'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Check, Globe, KeyRound, Mail, ShieldCheck, UserRound } from '@lucide/vue'

import {
  FunctionalIterablePage,
  Kinotic,
  Pageable,
  type IterablePage,
  type Page
} from '@kinotic-ai/core'
import { OidcProviderType, type ApplicationParticipantIdentity, type OrganizationMember, type PendingInviteSummary } from '@kinotic-ai/management-api'

import { CrudTable, FormDrawer, MemberDetail, SteppingDrawer, TINTS, useSteppingDrawer,
         type MemberSummary } from '@kinotic-ai/frontend-common'
import { PageHeader, TimePill } from '@kinotic-ai/frontend-common'
import { statusSeverity, useCrudTablePage } from '@kinotic-ai/frontend-common'
import type { CrudHeader } from '@kinotic-ai/frontend-common'
import type { DescriptiveIdentifiable } from '@kinotic-ai/frontend-common'
import CopyableUrl from '@/components/CopyableUrl.vue'
import { providerDisplayName } from '@/components/SocialAuthButton.vue'
import { KinoticStates } from '@/states'
import { PROVIDER_LOGOS } from '@/util/oidcConfigurations'
import { apiUrl, showErrorToast } from '@kinotic-ai/frontend-common'
import { createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('members')

/**
 * One table row — a member, pending while nobody has signed in as them yet, or, when {@link invite} is true, a
 * pending invitation to someone who already had an organization user.
 */
interface MemberRow extends MemberSummary {
  enabled?: boolean
  pending?: boolean
}

/**
 * Members of the organization (applicationId null) or of one application. Pending
 * invitations render inline ahead of the members, as Pending like members who have not
 * signed in yet; searching matches members on the server and pending invitations client-side.
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
  { field: 'created', header: 'Joined', sortable: false, width: '18%', optional: true }
]

/** One way an invitee can accept, with the provider's logo or, for the rest, an icon. */
interface SignInMethod {
  name: string
  logo?: string
  icon?: Component
}

const memberFormVisible = ref(false)
const memberEmail = ref('')
const memberDisplayName = ref('')
const sendInvitation = ref(true)
// The link of an invitation no email delivers, shown for the inviter to send on
const invitationLink = ref<{ email: string, url: string } | null>(null)
const saving = ref(false)
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


// An application's members are its users, which tells them apart from the organization's members
const title = computed<string>(() => props.applicationId !== null ? 'Users' : 'Members')
const addLabel = computed<string>(() => props.applicationId !== null ? 'Invite user' : 'Add member')

const membersDescription = computed<string>(() => {
  return props.applicationId !== null
      ? 'Everyone who signs in to this application, including pending invitations.'
      : 'Everyone in your organization, including people who have not signed in yet.'
})

const addDescription = computed<string>(() => {
  return props.applicationId !== null
      ? 'Send an invitation to sign in to this application.'
      : 'Add a person to your organization. They are pending until they first sign in.'
})

// The ways an invitee can accept, one row each in the invite form
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
  const membersPage = await loadMembers(pageable, searchText)

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
    content: [...inviteRows, ...(membersPage.content ?? [])],
    totalElements: (membersPage.totalElements ?? 0) + inviteTotal,
    cursor: undefined
  }

  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

async function loadMembers(pageable: Pageable, searchText: string | null): Promise<Page<MemberRow>> {
  const applicationId = props.applicationId
  let ret: Page<MemberRow>
  if (applicationId === null) {
    const page = searchText
      ? await Kinotic.members.searchMembers(searchText, pageable)
      : await Kinotic.members.findMembers(pageable)
    ret = { ...page, content: (page.content ?? []).map(member => toMemberRow(member)) }
  } else {
    const page = searchText
      ? await Kinotic.members.searchApplicationUsers(searchText, applicationId, pageable)
      : await Kinotic.members.findApplicationUsers(applicationId, pageable)
    ret = { ...page, content: (page.content ?? []).map(user => toApplicationUserRow(user)) }
  }
  return ret
}

function pageNumberOf(pageable: Pageable): number {
  return (pageable as Pageable & { pageNumber?: number }).pageNumber ?? 0
}

function toInviteRow(invite: PendingInviteSummary): MemberRow {
  return {
    id: invite.id ?? '',
    email: invite.email,
    displayName: invite.displayName,
    status: 'Pending',
    created: invite.created,
    invite: true
  }
}

function toMemberRow(member: OrganizationMember): MemberRow {
  return {
    id: member.id ?? '',
    email: member.email,
    displayName: member.displayName,
    status: member.pending ? 'Pending' : member.enabled ? 'Active' : 'Disabled',
    created: member.joined,
    enabled: member.enabled,
    pending: member.pending
  }
}

function toApplicationUserRow(user: ApplicationParticipantIdentity): MemberRow {
  return {
    id: user.id ?? '',
    email: user.email,
    displayName: user.displayName,
    status: user.enabled ? 'Active' : 'Disabled',
    created: user.created,
    enabled: user.enabled,
    pending: false
  }
}

/** The server rejects self-disable/remove; hiding the buttons mirrors that rule. */
function isSelf(item: MemberRow): boolean {
  return item.id === userState.connectedInfo?.participant?.id
}

function providerMethod(key: string): SignInMethod {
  const logo = PROVIDER_LOGOS[key as OidcProviderType]
  const name = `Signing in with ${providerDisplayName(key)}`
  return logo ? { name, logo } : { name, icon: markRaw(Globe) }
}

function openMemberForm() {
  memberEmail.value = ''
  memberDisplayName.value = ''
  sendInvitation.value = true
  memberFormVisible.value = true
}

async function submitMember() {
  const email = memberEmail.value.trim()
  if (!email || !email.includes('@')) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Please enter a valid email address', life: 5000 })
    return
  }
  saving.value = true
  try {
    const displayName = memberDisplayName.value.trim() || null
    if (props.applicationId !== null) {
      await Kinotic.members.inviteMember(email, displayName, props.applicationId)
      if (!(await showInvitationLink(email))) {
        toast.add({ severity: 'success', summary: 'Invitation sent', detail: `Invited ${email}`, life: 5000 })
      }
    } else {
      const added = await Kinotic.members.addMember(email, displayName, sendInvitation.value)
      // an address that already had an organization user is invited to accept, whatever the switch said
      const invited = added.id === null || sendInvitation.value
      if (!invited || !(await showInvitationLink(email))) {
        const detail = added.id === null
            ? `${email} already signs in to Kinotic organizations, so an invitation was emailed for them to accept`
            : sendInvitation.value
                ? `Added ${email} and emailed an invitation`
                : `Added ${email}; they sign in through your organization's provider`
        toast.add({ severity: 'success', summary: 'Member added', detail, life: 6000 })
      }
    }
    memberFormVisible.value = false
    // A leftover filter would hide the new Pending row; clearing it reloads the
    // table through CrudTable's search-prop watch, so refresh only when empty.
    if (tableSearch.value) {
      tableSearch.value = ''
    } else {
      refreshTable()
    }
  } catch (err) {
    showErrorToast(toast, props.applicationId !== null ? 'Failed to send invitation' : 'Failed to add member', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}

function sendInvitationTo(item: MemberRow) {
  run(async () => {
    await Kinotic.members.sendInvitation(item.email)
    await showInvitationLink(item.email)
  }, 'Invitation sent', 'Failed to send invitation')
}

// A server that shows links in the UI in place of email returns the invitation's link; shows it and resolves true when it
// did. The invitation is made by then, so a failed lookup resolves false rather than reporting the invitation failed
async function showInvitationLink(email: string): Promise<boolean> {
  let url: string | null = null
  try {
    url = await Kinotic.members.findInvitationLink(email, props.applicationId)
  } catch {
    // the invitation stands; its link is found again by sending the invitation again
  }
  if (url) {
    invitationLink.value = { email, url }
  }
  return Boolean(url)
}

function rowActions(item: MemberRow): MenuItem[] {
  if (item.invite) {
    // an application's invitations are sent once; an organization's pending members may be sent theirs again
    return [
      ...(props.applicationId === null
          ? [{ label: 'Send invitation again', icon: 'pi pi-send', command: () => sendInvitationTo(item) }]
          : []),
      { label: 'Remove', icon: 'pi pi-times', command: () => confirmCancelInvite(item) }
    ]
  }
  if (isSelf(item)) {
    return []
  }
  if (item.pending) {
    return [
      { label: 'Send invitation', icon: 'pi pi-send', command: () => sendInvitationTo(item) },
      { label: 'Remove member', icon: 'pi pi-trash', command: () => confirmRemove(item) }
    ]
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
    header: 'Remove',
    message: `Remove ${item.email}? Their invitation's accept link stops working.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Remove', severity: 'danger' },
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
  const message = props.applicationId !== null
      ? `Permanently remove ${item.email}? Their sign-in and stored credential are deleted.`
      : item.pending
          ? `Remove ${item.email} from your organization? Nobody has signed in as them yet, so they are deleted entirely.`
          : `Remove ${item.email} from your organization? Their other organizations stay as they are.`
  confirm.require({
    header: 'Remove member',
    message,
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
