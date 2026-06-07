<script lang="ts">
import { Component, Vue, Prop, Ref, Watch } from "vue-facing-decorator";
import CrudTable from "@/components/CrudTable.vue";
import ContainerMedium from "@/components/ContainerMedium.vue";
import { Kinotic, type IDataSource, Pageable } from "@kinotic-ai/core";
import {
  AuthType,
  type IamUser,
  type InviteOptions,
  type PendingInvite,
} from "@kinotic-ai/os-api";
import type { CrudHeader } from "@/types/CrudHeader";
import DatetimeUtil from "@/util/DatetimeUtil";
import { isDark as darkMode } from "@/composables/useTheme";
import { createDebug } from "@/util/debug";

import Dialog from "primevue/dialog";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Select from "primevue/select";
import Tag from "primevue/tag";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Toast from "primevue/toast";
import { useToast } from "primevue/usetoast";

const debug = createDebug("members");

interface AuthMethodOption {
  label: string;
  value: AuthType;
}

@Component({
  components: {
    CrudTable,
    ContainerMedium,
    Dialog,
    Button,
    InputText,
    Select,
    Tag,
    DataTable,
    Column,
    Toast,
  },
})
export default class Members extends Vue {
  /** null lists organization members; an application id lists that application's members. */
  @Prop({ default: null }) applicationId!: string | null;

  @Ref("crudTable") crudTable!: InstanceType<typeof CrudTable>;

  public AuthType = AuthType;
  public DatetimeUtil = DatetimeUtil;

  private toast = useToast();

  dataSource!: IDataSource<IamUser>;
  searchText: string = (this?.$route?.query.search as string) || "";

  headers: CrudHeader[] = [
    { field: "displayName", header: "Name", sortable: false },
    { field: "email", header: "Email", sortable: false },
    { field: "authType", header: "Auth type", sortable: false },
    { field: "enabled", header: "Status", sortable: false, centered: true },
    { field: "created", header: "Joined", sortable: false },
  ];

  // Invite dialog
  showInvite = false;
  inviteEmail = "";
  inviteDisplayName = "";
  inviteAuthType: AuthType = AuthType.LOCAL;
  inviteOptions: InviteOptions | null = null;
  inviteSubmitting = false;

  // Pending invites
  pendingInvites: PendingInvite[] = [];

  // Confirmation
  showConfirm = false;
  confirmMessage = "";
  confirmLoading = false;
  private confirmAction: (() => Promise<void>) | null = null;

  created(): void {
    this.dataSource = Kinotic.members.memberDataSource(this.applicationId);
  }

  mounted(): void {
    void this.loadPending();
  }

  get isDark(): boolean {
    return darkMode.value;
  }

  get authMethodOptions(): AuthMethodOption[] {
    const options: AuthMethodOption[] = [{ label: "Set a password", value: AuthType.LOCAL }];
    if (this.inviteOptions?.oidcEnabled) {
      const provider = this.inviteOptions.oidcProviderName || "SSO";
      options.push({ label: `Use ${provider} SSO`, value: AuthType.OIDC });
    }
    return options;
  }

  authTypeLabel(authType: AuthType | null): string {
    if (authType === AuthType.OIDC) return "SSO";
    if (authType === AuthType.LOCAL) return "Password";
    return "";
  }

  @Watch("$route.query.search")
  onRouteSearchQueryChanged(newVal: string): void {
    this.searchText = newVal || "";
  }

  updateRouteQuery(search: string): void {
    const query = { ...this?.$route?.query };
    if (search) {
      query.search = search;
    } else {
      delete query.search;
    }
    this.$router.replace({ query });
  }

  private refreshMembers(): void {
    this.crudTable?.find();
  }

  private async loadPending(): Promise<void> {
    try {
      const page = await Kinotic.members.findPendingInvites(
        this.applicationId,
        Pageable.create(0, 100, { orders: [] })
      );
      this.pendingInvites = page.content ?? [];
    } catch (error: unknown) {
      debug("Failed to load pending invites: %O", error);
    }
  }

  // ── Invite ────────────────────────────────────────────────────────────────

  async openInvite(): Promise<void> {
    this.inviteEmail = "";
    this.inviteDisplayName = "";
    this.inviteAuthType = AuthType.LOCAL;
    this.inviteOptions = null;
    this.showInvite = true;
    try {
      this.inviteOptions = await Kinotic.members.inviteOptions(this.applicationId);
    } catch (error: unknown) {
      this.displayError(this.errorMessage(error, "Failed to load invite options"));
    }
  }

  async submitInvite(): Promise<void> {
    const email = this.inviteEmail.trim();
    if (!email) {
      this.displayError("Email is required");
      return;
    }
    this.inviteSubmitting = true;
    try {
      await Kinotic.members.inviteMember(
        this.applicationId,
        email,
        this.inviteDisplayName.trim() || null,
        this.inviteAuthType
      );
      this.showInvite = false;
      this.toast.add({ severity: "success", summary: "Invitation sent", detail: `Invited ${email}`, life: 4000 });
      await this.loadPending();
      this.refreshMembers();
    } catch (error: unknown) {
      this.displayError(this.errorMessage(error, "Failed to send invitation"));
    } finally {
      this.inviteSubmitting = false;
    }
  }

  // ── Member actions ──────────────────────────────────────────────────────────

  async toggleEnabled(member: IamUser): Promise<void> {
    if (!member.id) return;
    try {
      await Kinotic.members.setMemberEnabled(member.id, !member.enabled);
      this.refreshMembers();
    } catch (error: unknown) {
      this.displayError(this.errorMessage(error, "Failed to update member"));
    }
  }

  confirmRemove(member: IamUser): void {
    this.askConfirm(
      `Remove ${member.displayName || member.email} from this organization?`,
      async () => {
        if (!member.id) return;
        await Kinotic.members.removeMember(member.id);
        this.toast.add({ severity: "success", summary: "Member removed", life: 4000 });
        this.refreshMembers();
      }
    );
  }

  confirmCancelInvite(invite: PendingInvite): void {
    this.askConfirm(`Cancel the invitation for ${invite.email}?`, async () => {
      if (!invite.id) return;
      await Kinotic.members.cancelInvite(invite.id);
      this.toast.add({ severity: "success", summary: "Invitation cancelled", life: 4000 });
      await this.loadPending();
    });
  }

  // ── Confirmation modal ──────────────────────────────────────────────────────

  private askConfirm(message: string, action: () => Promise<void>): void {
    this.confirmMessage = message;
    this.confirmAction = action;
    this.showConfirm = true;
  }

  async runConfirm(): Promise<void> {
    if (!this.confirmAction) return;
    this.confirmLoading = true;
    try {
      await this.confirmAction();
      this.showConfirm = false;
    } catch (error: unknown) {
      this.displayError(this.errorMessage(error, "Action failed"));
    } finally {
      this.confirmLoading = false;
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof Error && error.message) return error.message;
    return fallback;
  }

  private displayError(detail: string): void {
    this.toast.add({ severity: "error", summary: "Error", detail, life: 8000 });
  }
}
</script>

<template>
  <ContainerMedium>
    <div :class="['transition-colors', isDark ? 'text-surface-0' : 'text-surface-950']">
      <h1 :class="['mb-5 text-2xl font-semibold', isDark ? 'text-white' : 'text-surface-950']">Members</h1>

      <CrudTable
        ref="crudTable"
        createNewButtonText="Invite member"
        rowHoverColor=""
        transparent-dark-cards
        :data-source="dataSource"
        :headers="headers"
        emptyStateText="No members yet"
        :search="searchText"
        :show-pagination="false"
        @update:search="updateRouteQuery"
        @add-item="openInvite"
        class="!text-sm"
      >
        <template #item.displayName="{ item }">
          <span>{{ item.displayName || "—" }}</span>
        </template>
        <template #item.email="{ item }">
          <span>{{ item.email }}</span>
        </template>
        <template #item.authType="{ item }">
          <span>{{ authTypeLabel(item.authType) }}</span>
        </template>
        <template #item.enabled="{ item }">
          <Tag
            :value="item.enabled ? 'Active' : 'Disabled'"
            :severity="item.enabled ? 'success' : 'danger'"
          />
        </template>
        <template #item.created="{ item }">
          <span>{{ DatetimeUtil.formatMonthDayYear(item.created) }}</span>
        </template>

        <template #additional-actions="{ item }">
          <div class="flex items-center justify-center gap-2">
            <Button
              size="small"
              severity="secondary"
              text
              :label="item.enabled ? 'Disable' : 'Enable'"
              @click="toggleEnabled(item)"
            />
            <Button
              size="small"
              severity="danger"
              text
              icon="pi pi-trash"
              aria-label="Remove member"
              @click="confirmRemove(item)"
            />
          </div>
        </template>
      </CrudTable>

      <!-- Pending invitations -->
      <section v-if="pendingInvites.length > 0" class="mt-10">
        <h2 :class="['mb-4 text-lg font-semibold', isDark ? 'text-white' : 'text-surface-950']">
          Pending invitations
        </h2>
        <DataTable :value="pendingInvites" dataKey="id" class="!text-sm">
          <Column field="email" header="Email" />
          <Column header="Auth type">
            <template #body="{ data }">{{ authTypeLabel(data.authType) }}</template>
          </Column>
          <Column header="Expires">
            <template #body="{ data }">{{ DatetimeUtil.formatMonthDayYear(data.expiresAt) }}</template>
          </Column>
          <Column header="">
            <template #body="{ data }">
              <div class="flex justify-end">
                <Button
                  size="small"
                  severity="secondary"
                  text
                  label="Cancel"
                  @click="confirmCancelInvite(data)"
                />
              </div>
            </template>
          </Column>
        </DataTable>
      </section>

      <!-- Invite dialog -->
      <Dialog v-model:visible="showInvite" modal header="Invite member" :style="{ width: '440px' }">
        <div class="flex flex-col gap-4 py-2">
          <div class="flex flex-col gap-1">
            <label for="invite-email" class="text-sm font-medium">Email</label>
            <InputText id="invite-email" v-model="inviteEmail" placeholder="person@example.com" @keyup.enter="submitInvite" />
          </div>
          <div class="flex flex-col gap-1">
            <label for="invite-name" class="text-sm font-medium">Display name (optional)</label>
            <InputText id="invite-name" v-model="inviteDisplayName" placeholder="Jane Doe" @keyup.enter="submitInvite" />
          </div>
          <div class="flex flex-col gap-1">
            <label for="invite-auth" class="text-sm font-medium">Authentication method</label>
            <Select
              id="invite-auth"
              v-model="inviteAuthType"
              :options="authMethodOptions"
              optionLabel="label"
              optionValue="value"
            />
          </div>
        </div>
        <template #footer>
          <div class="flex justify-end gap-3">
            <Button label="Cancel" severity="secondary" :disabled="inviteSubmitting" @click="showInvite = false" />
            <Button label="Send invitation" :loading="inviteSubmitting" @click="submitInvite" />
          </div>
        </template>
      </Dialog>

      <!-- Confirmation dialog -->
      <Dialog v-model:visible="showConfirm" modal header="Please confirm" :closable="!confirmLoading" :style="{ width: '420px' }">
        <p class="py-2">{{ confirmMessage }}</p>
        <template #footer>
          <div class="flex justify-end gap-3">
            <Button label="Cancel" severity="secondary" :disabled="confirmLoading" @click="showConfirm = false" />
            <Button label="Confirm" severity="danger" :loading="confirmLoading" @click="runConfirm" />
          </div>
        </template>
      </Dialog>

      <Toast />
    </div>
  </ContainerMedium>
</template>
