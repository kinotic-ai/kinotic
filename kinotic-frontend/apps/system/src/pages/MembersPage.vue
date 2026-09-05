<template>
  <div class="flex flex-col">
    <PageHeader :title="applicationId ? 'Users' : 'Members'" :description="description" />
    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      :is-show-add-new="false"
      :disable-modifications="true"
      empty-state-text="No members"
      @update:search="tableSearch = $event"
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
        {{ item.created ? formatDate(item.created) : '—' }}
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
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
  PageHeader,
  DatetimeUtil,
  pageNumberOf,
  statusSeverity,
  useCrudTablePage,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'

/**
 * The people with access to an organization, or the users of one of its applications, with
 * pending invitations inline. Read-only: inviting, disabling and removing are the
 * organization's to do.
 */
const props = defineProps<{
  organizationId: string
  applicationId?: string
}>()

/** How many pending invitations the first page lists ahead of the members. */
const INVITE_PAGE_SIZE = 100

/** One table row — a member or, when {@link invite} is true, a pending invitation. */
interface MemberRow extends DescriptiveIdentifiable {
  id: string
  email: string
  displayName: string | null
  status: 'Invited' | 'Active' | 'Disabled'
  authType: string | null
  created: number | null
  invite?: boolean
}

const headers: CrudHeader[] = [
  { field: 'email', header: 'Email', sortable: false },
  { field: 'displayName', header: 'Name', sortable: false, optional: true },
  { field: 'status', header: 'Status', sortable: false },
  { field: 'authType', header: 'Auth type', sortable: false, optional: true },
  { field: 'created', header: 'Created', sortable: false, optional: true }
]

const description = computed(() => props.applicationId
    ? 'Everyone who signs in to this application, including pending invitations. Inviting, disabling and removing are the organization\'s to do.'
    : 'People with access to this organization, including pending invitations.')

const { tableSearch, dataSource, refreshTable } = useCrudTablePage(load)

const formatDate = DatetimeUtil.formatEpochDate

// Mirrors the portal MembersPage: pending invitations render inline ahead of the members
// on the first page; member search is server-side, invite filtering client-side.
async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const applicationId = props.applicationId ?? null
  const membersPage = searchText
      ? await Kinotic.systemOrganizations.searchMembers(searchText, props.organizationId, applicationId, pageable)
      : await Kinotic.systemOrganizations.findMembers(props.organizationId, applicationId, pageable)

  const invites = await Kinotic.systemOrganizations.findPendingInvites(props.organizationId, applicationId, Pageable.create(0, INVITE_PAGE_SIZE, null))
  let inviteRows = (invites.content ?? []).map(invite => toInviteRow(invite))
  let inviteTotal = invites.totalElements ?? inviteRows.length
  if (searchText) {
    const needle = searchText.trim().toLowerCase()
    inviteRows = inviteRows.filter(row =>
        row.email.toLowerCase().includes(needle) ||
        (row.displayName ?? '').toLowerCase().includes(needle))
    inviteTotal = inviteRows.length
  }
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
    authType: user.authType ?? null,
    created: user.created
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], () => refreshTable())
</script>
