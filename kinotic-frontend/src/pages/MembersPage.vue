<template>
  <div class="flex flex-col">
    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      create-new-button-text="Invite member"
      empty-state-text="No members yet"
      @add-item="openInviteDialog"
    >
      <template #item.displayName="{ item }">
        {{ item.displayName || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="statusSeverity(item)" />
      </template>

      <template #item.authType="{ item }">
        {{ item.authType || '—' }}
      </template>

      <template #item.created="{ item }">
        {{ formatDate(item.created) }}
      </template>

      <template #additional-actions="{ item }">
        <Button
          v-if="item.invite"
          text
          severity="danger"
          icon="pi pi-times"
          title="Cancel invitation"
          @click="confirmCancelInvite(item)"
        />
        <template v-else-if="!isSelf(item)">
          <Button
            text
            :icon="item.enabled ? 'pi pi-ban' : 'pi pi-check-circle'"
            :title="item.enabled ? 'Disable member' : 'Enable member'"
            @click="confirmToggleEnabled(item)"
          />
          <Button
            text
            severity="danger"
            icon="pi pi-trash"
            title="Remove member"
            @click="confirmRemove(item)"
          />
        </template>
      </template>
    </CrudTable>

    <Dialog v-model:visible="inviteDialogVisible" modal header="Invite member" :style="{ width: '28rem' }">
      <div class="flex flex-col gap-4">
        <div class="flex flex-col gap-1">
          <label for="invite-email" class="text-sm font-medium">Email</label>
          <InputText id="invite-email" v-model="inviteEmail" type="email" placeholder="person@example.com" autofocus />
        </div>
        <div class="flex flex-col gap-1">
          <label for="invite-name" class="text-sm font-medium">Display name (optional)</label>
          <InputText id="invite-name" v-model="inviteDisplayName" placeholder="Their name" @keyup.enter="sendInvite" />
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

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import {
  FunctionalIterablePage,
  Kinotic,
  Pageable,
  type IDataSource,
  type IterablePage,
  type Page
} from '@kinotic-ai/core'
import type { IamUser, PendingInviteSummary } from '@kinotic-ai/os-api'

import CrudTable from '@/components/CrudTable.vue'
import type { CrudHeader } from '@/types/CrudHeader'
import type { DescriptiveIdentifiable } from '@/types/DescriptiveIdentifiable'
import { StructuresStates } from '@/states'
import { apiUrl } from '@/util/helpers'
import { createDebug } from '@/util/debug'

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
 * addresses members only.
 */
@Component({
  components: { CrudTable, Button, Dialog, InputText, Tag }
})
export default class MembersPage extends Vue {
  @Prop({ default: null }) applicationId!: string | null

  headers: CrudHeader[] = [
    { field: 'email', header: 'Email', sortable: false },
    { field: 'displayName', header: 'Name', sortable: false },
    { field: 'status', header: 'Status', sortable: false },
    { field: 'authType', header: 'Auth type', sortable: false },
    { field: 'created', header: 'Created', sortable: false }
  ]

  inviteDialogVisible = false
  inviteEmail = ''
  inviteDisplayName = ''
  inviting = false
  socialProviderKeys: string[] = []

  private toast = useToast()
  private confirm = useConfirm()
  private userState = StructuresStates.getUserState()

  get dataSource(): IDataSource<DescriptiveIdentifiable> {
    return {
      findAll: (pageable: Pageable) => this.load(pageable, null),
      search: (searchText: string, pageable: Pageable) => this.load(pageable, searchText)
    }
  }

  get providersHint(): string {
    if (this.applicationId !== null) {
      return " or signing in with any provider configured for this application"
    }
    if (this.socialProviderKeys.length === 0) {
      return ''
    }
    const names = this.socialProviderKeys.map(key => this.providerDisplayName(key))
    return ' or signing in with ' + names.join(', ')
  }

  async mounted() {
    // Same source the login page uses for its social buttons; org invitees are offered these.
    if (this.applicationId === null) {
      try {
        const res = await fetch(apiUrl('/api/login/providers'), { credentials: 'same-origin' })
        if (res.ok) {
          const data = await res.json()
          if (Array.isArray(data)) this.socialProviderKeys = data
        }
      } catch (err) {
        debug('Failed to load providers: %O', err)
      }
    }
  }

  private async load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
    const membersPage = searchText
      ? await Kinotic.members.searchMembers(searchText, this.applicationId, pageable)
      : await Kinotic.members.findMembers(this.applicationId, pageable)

    let inviteRows: MemberRow[] = []
    let inviteTotal = 0
    if (!searchText) {
      const invites = await Kinotic.members.findPendingInvites(this.applicationId, Pageable.create(0, 100, null))
      inviteTotal = invites.totalElements ?? invites.content?.length ?? 0
      // Prepended on the first page only, so deeper pages stay pure member pages.
      if (this.pageNumberOf(pageable) === 0) {
        inviteRows = (invites.content ?? []).map(invite => this.toInviteRow(invite))
      }
    }

    const page: Page<DescriptiveIdentifiable> = {
      content: [...inviteRows, ...(membersPage.content ?? []).map(user => this.toMemberRow(user))],
      totalElements: (membersPage.totalElements ?? 0) + inviteTotal,
      cursor: undefined
    }

    return new FunctionalIterablePage(pageable, page, (next: Pageable) => this.load(next, searchText))
  }

  private pageNumberOf(pageable: Pageable): number {
    return (pageable as Pageable & { pageNumber?: number }).pageNumber ?? 0
  }

  private toInviteRow(invite: PendingInviteSummary): MemberRow {
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

  private toMemberRow(user: IamUser): MemberRow {
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

  statusSeverity(item: MemberRow): string {
    switch (item.status) {
      case 'Invited':  return 'info'
      case 'Active':   return 'success'
      case 'Disabled': return 'danger'
    }
  }

  formatDate(epochMillis: number | null): string {
    return epochMillis ? new Date(epochMillis).toLocaleDateString() : '—'
  }

  /** The server rejects self-disable/remove; hiding the buttons mirrors that rule. */
  isSelf(item: MemberRow): boolean {
    return item.id === this.userState.connectedInfo?.participant?.id
  }

  providerDisplayName(key: string): string {
    switch (key) {
      case 'google':   return 'Google'
      case 'azure-ad': return 'Microsoft'
      default:         return key.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
    }
  }

  openInviteDialog() {
    this.inviteEmail = ''
    this.inviteDisplayName = ''
    this.inviteDialogVisible = true
  }

  async sendInvite() {
    const email = this.inviteEmail.trim()
    if (!email || !email.includes('@')) {
      this.toast.add({ severity: 'error', summary: 'Error', detail: 'Please enter a valid email address', life: 5000 })
      return
    }
    this.inviting = true
    try {
      await Kinotic.members.inviteMember(email, this.inviteDisplayName.trim() || null, this.applicationId)
      this.inviteDialogVisible = false
      this.toast.add({ severity: 'success', summary: 'Invitation sent', detail: `Invited ${email}`, life: 5000 })
      this.refreshTable()
    } catch (err) {
      this.toast.add({ severity: 'error', summary: 'Error', detail: this.errorMessage(err, 'Failed to send invitation'), life: 8000 })
    } finally {
      this.inviting = false
    }
  }

  confirmCancelInvite(item: MemberRow) {
    this.confirm.require({
      header: 'Cancel invitation',
      message: `Cancel the invitation for ${item.email}? Their accept link stops working.`,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Cancel invitation', severity: 'danger' },
      rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
      accept: () => this.run(() => Kinotic.members.cancelInvite(item.id), 'Invitation cancelled', 'Failed to cancel invitation')
    })
  }

  confirmToggleEnabled(item: MemberRow) {
    const disabling = item.enabled === true
    this.confirm.require({
      header: disabling ? 'Disable member' : 'Enable member',
      message: disabling
        ? `Disable ${item.email}? They can no longer sign in; sessions already open last until they expire.`
        : `Enable ${item.email}? They can sign in again.`,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: disabling ? 'Disable' : 'Enable', severity: disabling ? 'danger' : 'primary' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: () => this.run(
          () => Kinotic.members.setMemberEnabled(item.id, !item.enabled),
          disabling ? 'Member disabled' : 'Member enabled',
          'Failed to update member')
    })
  }

  confirmRemove(item: MemberRow) {
    this.confirm.require({
      header: 'Remove member',
      message: `Permanently remove ${item.email}? Their sign-in and stored credential are deleted.`,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Remove', severity: 'danger' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: () => this.run(() => Kinotic.members.removeMember(item.id), 'Member removed', 'Failed to remove member')
    })
  }

  private async run(action: () => Promise<void>, successMessage: string, failureMessage: string) {
    try {
      await action()
      this.toast.add({ severity: 'success', summary: successMessage, life: 4000 })
      this.refreshTable()
    } catch (err) {
      this.toast.add({ severity: 'error', summary: 'Error', detail: this.errorMessage(err, failureMessage), life: 8000 })
    }
  }

  private refreshTable() {
    const table = this.$refs.crudTable as InstanceType<typeof CrudTable> | undefined
    table?.find()
  }

  private errorMessage(err: unknown, fallback: string): string {
    return err instanceof Error && err.message ? err.message : fallback
  }
}
</script>
