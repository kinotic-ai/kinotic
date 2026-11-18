<template>
  <div class="flex flex-col">
    <PageHeader :title="title" :description="description" />

    <!-- the people at a glance, from the server's own totals -->
    <section v-if="peopleTotal !== null"
             class="mb-4 flex flex-wrap items-center gap-x-10 gap-y-3 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30">
      <div class="flex items-center gap-3">
        <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', scopeTint(scope)]">
          <Users :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="leading-tight">
          <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">{{ title }}</div>
          <div class="mt-0.5 text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ peopleTotal }}</div>
        </div>
      </div>
      <div v-if="inviteTotal > 0" class="leading-tight">
        <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Pending invitations</div>
        <div class="mt-0.5 flex items-center gap-2 text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">
          <span class="h-2 w-2 rounded-full bg-sky-500" aria-hidden="true" />{{ inviteTotal }}
        </div>
      </div>
    </section>
    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :is-show-add-new="systemScope"
      :disable-modifications="!systemScope"
      create-new-button-text="Add user"
      :row-actions="systemScope ? rowActions : undefined"
      empty-state-text="No members"
      @add-item="openAddForm"
      :selected-id="highlightedMember?.id"
      @update:search="tableSearch = $event"
      @row-hover="row => hoverMember(row as MemberSummary)"
      @on-row-click="row => openMember(row as MemberSummary)"
    >
      <template #item.email="{ item, index }">
        <span class="flex min-w-0 items-center gap-2.5">
          <InitialsTile :name="item.displayName || item.email" :index="index" />
          <span class="min-w-0">
            <span class="block truncate font-sans text-sm font-semibold text-surface-950 dark:text-surface-0">{{ item.displayName || item.email }}</span>
            <span v-if="item.displayName" class="block truncate text-xs text-muted-color" v-tooltip.top="item.email">{{ item.email }}</span>
          </span>
        </span>
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
      <MemberDetail v-if="selectedMember" :member="selectedMember" :tint="scopeTint(scope)" :index="position - 1" />
      <!-- a system user's grants live on the Access page; an organization's people are granted in the portal -->
      <div v-if="selectedMember && !organizationId" class="mt-4 flex justify-end">
        <Button label="Manage access" icon="pi pi-key" size="small" severity="secondary" outlined
                @click="router.push({ name: 'system-access', query: { subject: selectedMember.id } })" />
      </div>
    </SteppingDrawer>

    <FormDrawer :visible="addFormVisible" title="Add user"
                description="A system user may do what the roles granted on the Access page allow."
                @close="addFormVisible = false">
      <form id="add-system-user-form" class="flex flex-col gap-6" @submit.prevent="addUser">
        <div>
          <label for="system-user-email" class="mb-2 block text-sm font-medium">Email</label>
          <InputText id="system-user-email" v-model="newEmail" type="email" autocomplete="off" autofocus class="w-full" />
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">
            The user signs in through a sign-in provider whose account has this email, or with a password you reset for them.
          </p>
        </div>
        <div>
          <label for="system-user-name" class="mb-2 block text-sm font-medium">Name <span class="font-normal text-muted-color">(optional)</span></label>
          <InputText id="system-user-name" v-model="newDisplayName" autocomplete="off" class="w-full" />
        </div>
      </form>
      <template #footer>
        <Button type="button" label="Cancel" severity="secondary" outlined @click="addFormVisible = false" />
        <Button type="submit" form="add-system-user-form" label="Add user" :loading="adding" :disabled="newEmail.trim() === ''" />
      </template>
    </FormDrawer>

    <MachineSecretDialog v-model="resetPassword" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Users } from '@lucide/vue'
import Button from 'primevue/button'
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
import type { ApplicationParticipantIdentity, OrganizationParticipantIdentity, SystemParticipantIdentity } from '@kinotic-ai/management-api'
import {
  CrudTable,
  FormDrawer,
  InitialsTile,
  MachineSecretDialog,
  PageHeader,
  TimePill,
  statusSeverity,
  MemberDetail,
  SteppingDrawer,
  useCrudTablePage,
  useSteppingDrawer,
  showErrorToast,
  type MachineSecret,
  type MemberSummary,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import { scopeTint, type Scope } from '@/util/scope'

/**
 * The system users when no scope is given, otherwise the people with access to an
 * organization or the users of one of its applications, each pending until they first sign in.
 * Read-only: an organization's people are the organization's to manage, and the system's are
 * the identity provider's.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
}>()

const router = useRouter()

const headers: CrudHeader[] = [
  { field: 'email', header: 'Person', sortable: false, width: '40%' },
  { field: 'status', header: 'Status', sortable: false, width: '16%' },
  { field: 'created', header: 'Joined', sortable: false, optional: true, width: '22%' }
]

// An organization's people are its members; an application's and the system's are its users
const title = computed(() => props.organizationId && !props.applicationId ? 'Members' : 'Users')

const description = computed(() => {
  let ret: string
  if (!props.organizationId) {
    ret = 'System users who sign in to this console. A user you add signs in through a sign-in provider, or with a password you reset for them.'
  } else if (props.applicationId) {
    ret = 'Everyone who signs in to this application, including people who have not signed in yet. Adding, disabling and removing are the organization\'s to do.'
  } else {
    ret = 'People with access to this organization, including people who have not signed in yet.'
  }
  return ret
})

const scope = computed<Scope>(() => ({ organizationId: props.organizationId, applicationId: props.applicationId }))

// the system's own users are added, disabled and given a password here; an organization's are its own to manage
const systemScope = computed(() => !props.organizationId)

/** The server's totals behind the summary: the people, and the invitations still pending. */
const peopleTotal = ref<number | null>(null)
const inviteTotal = ref(0)

const { tableSearch, dataSource, refreshTable, run, shownRows } = useCrudTablePage(load)

const toast = useToast()
const confirm = useConfirm()
const addFormVisible = ref(false)
const newEmail = ref('')
const newDisplayName = ref('')
const adding = ref(false)
const resetPassword = ref<MachineSecret | null>(null)

const shownMembers = computed(() => shownRows.value as MemberSummary[])
const { selected: selectedMember, visible: drawerVisible, position, open: openMember, step: stepMember,
        highlighted: highlightedMember, hover: hoverMember } = useSteppingDrawer(shownMembers, member => member.id)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const organizationId = props.organizationId
  const page = organizationId
      ? await organizationMembers(organizationId, pageable, searchText)
      : await systemUsers(pageable, searchText)

  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

/** The system users; SYSTEM scope is added to, not invited into, so there are no invitations to fold in. */
async function systemUsers(pageable: Pageable, searchText: string | null): Promise<Page<DescriptiveIdentifiable>> {
  const users = searchText
      ? await Kinotic.systemMembers.searchUsers(searchText, pageable)
      : await Kinotic.systemMembers.findUsers(pageable)

  if (!searchText) {
    peopleTotal.value = users.totalElements ?? 0
    inviteTotal.value = 0
  }
  return {
    content: (users.content ?? []).map(user => toUserSummary(user)),
    totalElements: users.totalElements ?? 0,
    cursor: undefined
  }
}

// The scope's people, each pending until they first sign in; the summary also counts the invitations still out
async function organizationMembers(organizationId: string,
                                   pageable: Pageable,
                                   searchText: string | null): Promise<Page<DescriptiveIdentifiable>> {
  const applicationId = props.applicationId ?? null
  const membersPage = await scopeMembers(organizationId, applicationId, pageable, searchText)
  // the summary counts what exists, so a search leaves it as it was
  if (!searchText) {
    const invites = await Kinotic.systemOrganizations.findPendingInvites(organizationId, applicationId, Pageable.create(0, 1, null))
    peopleTotal.value = membersPage.totalElements ?? 0
    inviteTotal.value = invites.totalElements ?? 0
  }
  return membersPage
}

/** The organization's members, or the users of one of its applications. */
async function scopeMembers(organizationId: string,
                            applicationId: string | null,
                            pageable: Pageable,
                            searchText: string | null): Promise<Page<MemberSummary>> {
  let ret: Page<MemberSummary>
  if (applicationId === null) {
    const page = searchText
        ? await Kinotic.systemOrganizations.searchMembers(searchText, organizationId, pageable)
        : await Kinotic.systemOrganizations.findMembers(organizationId, pageable)
    ret = { ...page, content: (page.content ?? []).map(member => toMemberSummary(member)) }
  } else {
    const page = searchText
        ? await Kinotic.systemOrganizations.searchApplicationUsers(searchText, organizationId, applicationId, pageable)
        : await Kinotic.systemOrganizations.findApplicationUsers(organizationId, applicationId, pageable)
    ret = { ...page, content: (page.content ?? []).map(user => toApplicationUserSummary(user)) }
  }
  return ret
}

function openAddForm(): void {
  newEmail.value = ''
  newDisplayName.value = ''
  addFormVisible.value = true
}

async function addUser(): Promise<void> {
  adding.value = true
  try {
    await Kinotic.systemMembers.addUser(newEmail.value.trim(), newDisplayName.value.trim() || null)
    addFormVisible.value = false
    toast.add({ severity: 'success', summary: 'User added', life: 4000 })
    refreshTable()
  } catch (err) {
    showErrorToast(toast, 'Failed to add the user', err, { life: 8000 })
  } finally {
    adding.value = false
  }
}

function rowActions(member: MemberSummary): MenuItem[] {
  const disabling = member.status !== 'Disabled'
  return [
    { label: 'Reset password', icon: 'pi pi-key', command: () => confirmResetPassword(member) },
    {
      label: disabling ? 'Disable' : 'Enable',
      icon: disabling ? 'pi pi-ban' : 'pi pi-check-circle',
      command: () => confirmSetEnabled(member, !disabling)
    }
  ]
}

function confirmSetEnabled(member: MemberSummary, enabled: boolean): void {
  confirm.require({
    header: enabled ? 'Enable user' : 'Disable user',
    message: enabled
      ? `Enable ${member.email}? They can sign in to this console again.`
      : `Disable ${member.email}? They can no longer sign in to this console.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: enabled ? 'Enable' : 'Disable', severity: enabled ? 'primary' : 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.systemMembers.setUserEnabled(member.id, enabled),
                      enabled ? 'User enabled' : 'User disabled', 'Failed to update the user')
  })
}

function confirmResetPassword(member: MemberSummary): void {
  confirm.require({
    header: 'Reset password',
    message: `Give ${member.email} a new password? The one they have stops working, and the new one is shown once for you to pass on.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Reset password', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        const password = await Kinotic.systemMembers.resetUserPassword(member.id)
        resetPassword.value = {
          title: `Password reset — ${member.email}`,
          clientId: member.email,
          clientSecret: password,
          idLabel: 'Email',
          secretLabel: 'Password'
        }
        refreshTable()
      } catch (err) {
        showErrorToast(toast, 'Failed to reset the password', err, { life: 8000 })
      }
    }
  })
}

function toMemberSummary(member: OrganizationParticipantIdentity): MemberSummary {
  return {
    id: member.id ?? '',
    email: member.email,
    displayName: member.displayName,
    status: member.pending ? 'Pending' : member.enabled ? 'Active' : 'Disabled',
    created: member.created
  }
}

function toApplicationUserSummary(user: ApplicationParticipantIdentity): MemberSummary {
  return {
    id: user.id ?? '',
    email: user.email,
    displayName: user.displayName,
    status: user.pending ? 'Pending' : user.enabled ? 'Active' : 'Disabled',
    created: user.created
  }
}

function toUserSummary(user: SystemParticipantIdentity): MemberSummary {
  return {
    id: user.id ?? '',
    email: user.email,
    displayName: user.displayName,
    status: !user.enabled ? 'Disabled' : user.pending ? 'Pending' : 'Active',
    created: user.created
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], () => refreshTable())
</script>
