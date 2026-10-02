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
      :is-show-add-new="false"
      :disable-modifications="true"
      empty-state-text="No members"
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

      <template #item.authType="{ item }">
        <TableChip v-if="item.authType" :icon="item.authType === 'OIDC' ? ShieldCheck : KeyRound">
          {{ authTypeLabel(item.authType) }}
        </TableChip>
        <span v-else class="text-muted-color">—</span>
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
    </SteppingDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { KeyRound, ShieldCheck, Users } from '@lucide/vue'
import Tag from 'primevue/tag'

import {
  FunctionalIterablePage,
  Kinotic,
  Pageable,
  type IterablePage,
  type Page
} from '@kinotic-ai/core'
import type { PendingInviteSummary, UserParticipantIdentity } from '@kinotic-ai/management-api'
import {
  CrudTable,
  InitialsTile,
  PageHeader,
  TableChip,
  TimePill,
  pageNumberOf,
  statusSeverity,
  MemberDetail,
  SteppingDrawer,
  authTypeLabel,
  useCrudTablePage,
  useSteppingDrawer,
  type MemberSummary,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

import { scopeTint, type Scope } from '@/util/scope'

/**
 * The platform's operators when no scope is given, otherwise the people with access to an
 * organization or the users of one of its applications, with pending invitations inline.
 * Read-only: an organization's people are the organization's to manage, and the platform's are
 * the identity provider's.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
}>()

/** How many pending invitations the first page lists ahead of the members. */
const INVITE_PAGE_SIZE = 100

/** One table row — a member or, when {@link invite} is true, a pending invitation. */
const headers: CrudHeader[] = [
  { field: 'email', header: 'Person', sortable: false, width: '40%' },
  { field: 'status', header: 'Status', sortable: false, width: '16%' },
  { field: 'authType', header: 'Signs in with', sortable: false, optional: true, width: '22%' },
  { field: 'created', header: 'Created', sortable: false, optional: true, width: '22%' }
]

// An organization's people are its members; an application's and the platform's are its users
const title = computed(() => props.organizationId && !props.applicationId ? 'Members' : 'Users')

const description = computed(() => {
  let ret: string
  if (!props.organizationId) {
    ret = 'Platform operators who sign in to this console.'
  } else if (props.applicationId) {
    ret = 'Everyone who signs in to this application, including pending invitations. Inviting, disabling and removing are the organization\'s to do.'
  } else {
    ret = 'People with access to this organization, including pending invitations.'
  }
  return ret
})

const scope = computed<Scope>(() => ({ organizationId: props.organizationId, applicationId: props.applicationId }))

/** The server's totals behind the summary: the people, and the invitations still pending. */
const peopleTotal = ref<number | null>(null)
const inviteTotal = ref(0)

const { tableSearch, dataSource, refreshTable, shownRows } = useCrudTablePage(load)

const shownMembers = computed(() => shownRows.value as MemberSummary[])
const { selected: selectedMember, visible: drawerVisible, position, open: openMember, step: stepMember,
        highlighted: highlightedMember, hover: hoverMember } = useSteppingDrawer(shownMembers, member => member.id)

async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const organizationId = props.organizationId
  const page = organizationId
      ? await organizationMembers(organizationId, pageable, searchText)
      : await platformOperators(pageable, searchText)

  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

/** The platform's operators; SYSTEM scope is not invited into, so there is nothing to fold in. */
async function platformOperators(pageable: Pageable, searchText: string | null): Promise<Page<DescriptiveIdentifiable>> {
  const users = searchText
      ? await Kinotic.systemMembers.searchUsers(searchText, pageable)
      : await Kinotic.systemMembers.findUsers(pageable)

  if (!searchText) {
    peopleTotal.value = users.totalElements ?? 0
    inviteTotal.value = 0
  }
  return {
    content: (users.content ?? []).map(user => toMemberSummary(user)),
    totalElements: users.totalElements ?? 0,
    cursor: undefined
  }
}

// Mirrors the portal MembersPage: pending invitations render inline ahead of the members
// on the first page; member search is server-side, invite filtering client-side.
async function organizationMembers(organizationId: string,
                                   pageable: Pageable,
                                   searchText: string | null): Promise<Page<DescriptiveIdentifiable>> {
  const applicationId = props.applicationId ?? null
  const membersPage = searchText
      ? await Kinotic.systemOrganizations.searchMembers(searchText, organizationId, applicationId, pageable)
      : await Kinotic.systemOrganizations.findMembers(organizationId, applicationId, pageable)

  const invites = await Kinotic.systemOrganizations.findPendingInvites(organizationId, applicationId, Pageable.create(0, INVITE_PAGE_SIZE, null))
  let inviteRows = (invites.content ?? []).map(invite => toInviteRow(invite))
  let inviteCount = invites.totalElements ?? inviteRows.length
  // the summary counts what exists, so a search leaves it as it was
  if (!searchText) {
    peopleTotal.value = membersPage.totalElements ?? 0
    inviteTotal.value = inviteCount
  }
  if (searchText) {
    const needle = searchText.trim().toLowerCase()
    inviteRows = inviteRows.filter(row =>
        row.email.toLowerCase().includes(needle) ||
        (row.displayName ?? '').toLowerCase().includes(needle))
    inviteCount = inviteRows.length
  }
  if (pageNumberOf(pageable) !== 0) {
    inviteRows = []
  }

  return {
    content: [...inviteRows, ...(membersPage.content ?? []).map(user => toMemberSummary(user))],
    totalElements: (membersPage.totalElements ?? 0) + inviteCount,
    cursor: undefined
  }
}

function toInviteRow(invite: PendingInviteSummary): MemberSummary {
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

function toMemberSummary(user: UserParticipantIdentity): MemberSummary {
  return {
    id: user.id ?? '',
    email: user.email,
    displayName: user.displayName,
    status: user.enabled ? 'Active' : 'Disabled',
    authType: user.authType ?? null,
    created: user.created
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], () => refreshTable())
</script>
