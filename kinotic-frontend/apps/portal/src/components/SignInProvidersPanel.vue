<template>
  <div class="flex flex-col gap-4 pt-4">
    <p class="max-w-[880px] text-sm text-muted-color">{{ intro }}</p>

    <CrudTable
      ref="crudTable"
      :headers="headers"
      :data-source="dataSource"
      :search="tableSearch"
      create-new-button-text="Add provider"
      :empty-state-text="emptyStateText"
      :row-actions="rowActions"
      :show-pagination="false"
      :selected-id="highlightedRow?.id"
      @update:search="tableSearch = $event"
      @row-hover="row => hoverRow(row as ConfigurationRow)"
      @on-row-click="row => openRow(row as ConfigurationRow)"
      @add-item="openForm(null)"
    >
      <template #item.provider="{ item }">
        <span class="flex items-center gap-2 font-sans">
          <img v-if="item.logo" :src="item.logo" alt="" class="h-4 w-4 shrink-0" />
          <KeyRound v-else :size="16" :stroke-width="1.75" class="shrink-0 text-surface-500" aria-hidden="true" />
          {{ item.providerName }}
        </span>
      </template>

      <template #item.providerAccount="{ item }">
        {{ item.providerAccount || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="item.enabled ? 'success' : 'secondary'" />
      </template>

      <template #item.secret="{ item }">
        <Tag v-if="item.secret" :value="item.secret" :severity="item.secretSeverity" />
        <template v-else>—</template>
      </template>

      <template #item.created="{ item }">
        <TimePill :date="item.created" />
      </template>
    </CrudTable>

    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="shownRows.length" @step="stepRow">
      <template #title>
        <span v-if="selectedRow" class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ selectedRow.name }}</span>
      </template>
      <div v-if="selectedRow" class="flex max-w-[720px] flex-col gap-6">
        <FactList :facts="facts(selectedRow.source)" />
        <template v-if="selectedRow.source.kind === 'oidc'">
          <CopyableUrl v-if="!isManaged(selectedRow.source.configuration)" label="Callback URL" :url="selectedCallbackUrl"
                       help="Register this as a redirect URI at the provider; sign-ins through this provider fail until it is." />
          <CopyableUrl v-if="startUrlOf(selectedRow.source.configuration)" label="Direct sign-in link" :url="startUrlOf(selectedRow.source.configuration)"
                       :help="START_URL_HELP" />
        </template>
        <div>
          <Button label="Edit" icon="pi pi-pencil" severity="secondary" outlined @click="openForm(selectedRow.source)" />
        </div>
      </div>
    </SteppingDrawer>

    <SignInProviderDrawer v-model:visible="formVisible" :configuration="editingOidc" :password="editingPassword"
                          :password-scopes="passwordScopes" :application-id="applicationId" :tenants="tenants"
                          @saved="onSaved" />

    <FormDrawer :visible="copyFormVisible" title="Copy to application" :description="copyDescription"
                @close="copyFormVisible = false">
      <form id="copy-configuration-form" @submit.prevent="submitCopy">
        <div v-if="copied" class="flex flex-col gap-5">
          <p class="text-sm text-surface-700 dark:text-surface-200">
            The copy has a callback URL of its own. Add it to the application registration at the provider as a redirect URI;
            sign-ins through the copy fail until it is registered there.
          </p>
          <CopyableUrl label="Callback URL of the copy" :url="copiedCallbackUrl" />
        </div>
        <div v-else>
          <label for="copy-application" class="mb-2 block text-sm font-medium">Application</label>
          <Select input-id="copy-application" v-model="copyApplicationId" :options="copyTargets" option-label="name" option-value="id"
                  placeholder="Choose an application" class="w-full" :empty-message="'No other application'" />
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">
            The copy carries the provider, account, client ID and secret, and is offered at that application's login.
          </p>
        </div>
      </form>
      <template #footer>
        <Button v-if="copied" type="button" label="Done" @click="copyFormVisible = false" />
        <template v-else>
          <Button type="button" label="Cancel" severity="secondary" outlined @click="copyFormVisible = false" />
          <Button type="submit" form="copy-configuration-form" label="Copy" icon="pi pi-copy" :loading="copying" :disabled="copyApplicationId === null" />
        </template>
      </template>
    </FormDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Building2, CalendarClock, Fingerprint, Globe, KeyRound, UsersRound } from '@lucide/vue'
import { FunctionalIterablePage, Kinotic, Pageable, type IterablePage, type Page } from '@kinotic-ai/core'
import {
  ClientRegistrationType,
  OidcConfigurationType,
  type OidcProviderType,
  type OwnedOidcConfiguration,
  type PasswordConfiguration,
  type Tenant
} from '@kinotic-ai/management-api'
import {
  CrudTable,
  DatetimeUtil,
  FactList,
  FormDrawer,
  SteppingDrawer,
  TimePill,
  showErrorToast,
  useCrudTablePage,
  useSteppingDrawer,
  type CrudHeader,
  type DescriptiveIdentifiable
} from '@kinotic-ai/frontend-common'
import CopyableUrl from '@/components/CopyableUrl.vue'
import SignInProviderDrawer from '@/components/SignInProviderDrawer.vue'
import { providerDisplayName } from '@/components/SocialAuthButton.vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { PASSWORD_PROVIDER_NAME, PROVIDER_LOGOS, START_URL_HELP, startUrlOf, type SignInProvider } from '@/util/oidcConfigurations'

/** One table row: a sign-in provider of the organization's and the columns it is listed by. */
interface ConfigurationRow extends DescriptiveIdentifiable {
  source: SignInProvider
  name: string
  providerName: string
  /** The provider's logo, or null for an email and a password, which is shown with an icon. */
  logo: string | null
  providerAccount: string | null
  scope: string
  enabled: boolean
  status: string
  /** How the provider's client is held, or null for an email and a password, which has none. */
  secret: string | null
  secretSeverity: 'info' | 'warn'
  created: string | null
}

/**
 * The organization's own sign-in providers of one sign-in scope, its OIDC configurations and its email and password,
 * listed with what a provider is given, and added, edited, copied, enabled, disabled and deleted from here: the
 * organization's login when applicationId is null, otherwise the application's login and its tenants', grouped by
 * tenant.
 */
const props = defineProps<{
  applicationId: string | null
}>()

const toast = useToast()
const confirm = useConfirm()
const { tableSearch, dataSource, refreshTable, run, shownRows: rows, removeRow } = useCrudTablePage(load)
const shownRows = computed(() => rows.value as ConfigurationRow[])
const { selected: selectedRow, visible: drawerVisible, position, open: openRow, step: stepRow,
        highlighted: highlightedRow, hover: hoverRow } = useSteppingDrawer(shownRows, row => row.id)

const tenants = ref<Tenant[]>([])
/** The scopes the table lists that take an email and a password already, by tenant id, null for the scope's own login. */
const passwordScopes = ref<(string | null)[]>([])
const formVisible = ref(false)
const editingOidc = ref<OwnedOidcConfiguration | null>(null)
const editingPassword = ref<PasswordConfiguration | null>(null)
const selectedCallbackUrl = ref<string | null>(null)

const copyFormVisible = ref(false)
const copySource = ref<OwnedOidcConfiguration | null>(null)
const copyApplicationId = ref<string | null>(null)
const copying = ref(false)
const copied = ref<OwnedOidcConfiguration | null>(null)
const copiedCallbackUrl = ref<string | null>(null)

const formatDate = DatetimeUtil.formatLocaleDate

const headers = computed<CrudHeader[]>(() => [
  { field: 'name', header: 'Name', sortable: false, width: props.applicationId === null ? '22%' : '18%' },
  { field: 'provider', header: 'Provider', sortable: false, width: '14%' },
  ...(props.applicationId === null ? [] : [{ field: 'scope', header: 'Offered at', sortable: false, width: '16%' }]),
  { field: 'providerAccount', header: 'Account', sortable: false, width: '22%', optional: true },
  { field: 'status', header: 'Status', sortable: false, width: '11%' },
  { field: 'secret', header: 'Client', sortable: false, width: '13%', optional: true },
  { field: 'created', header: 'Created', sortable: false, width: '12%', optional: true }
])

const intro = computed(() => props.applicationId === null
    ? 'How your organization\'s members sign in to the portal: identity providers such as your Okta or Microsoft Entra ID directory, each offered on your organization\'s login page once its callback URL is registered at the provider, and an email and a password, offered only when you add it.'
    : 'How this application\'s users sign in and sign up: identity providers, offered on the application\'s login page, or on one tenant\'s for a provider of that tenant, and an email and a password, offered only when you add it. Google and Microsoft can be managed by Kinotic, which works with no setup; a provider with your own credentials works once its callback URL is registered at the provider.')
const emptyStateText = computed(() => props.applicationId === null
    ? 'No providers yet. Add one so your members sign in with your organization\'s own accounts.'
    : 'No providers yet. Add one so this application\'s users can sign in.')

// every application but the one the source is offered at
const copyTargets = computed(() => APPLICATION_STATE.allApplications.filter(application => application.id !== props.applicationId))
const copyDescription = computed(() => copySource.value ? `Offer ${copySource.value.name} at another application's login.` : undefined)

watch(() => props.applicationId, loadTenants, { immediate: true })

watch(selectedRow, async row => {
  selectedCallbackUrl.value = null
  if (row?.source.kind === 'oidc' && row.source.configuration.id) {
    selectedCallbackUrl.value = await findCallbackUrl(row.source.configuration.id)
  }
})

async function loadTenants(applicationId: string | null): Promise<void> {
  tenants.value = []
  if (applicationId !== null) {
    try {
      tenants.value = (await Kinotic.tenants.findAllForApplication(applicationId, Pageable.create(0, 100))).content ?? []
    } catch (err) {
      showErrorToast(toast, 'Failed to load the application\'s tenants', err)
    }
  }
}

function inScope(provider: SignInProvider): boolean {
  let ret: boolean
  if (provider.kind === 'password') {
    ret = provider.configuration.applicationId === props.applicationId
  } else if (props.applicationId === null) {
    ret = provider.configuration.type === OidcConfigurationType.ORGANIZATION
  } else {
    ret = provider.configuration.type !== OidcConfigurationType.ORGANIZATION && provider.configuration.applicationId === props.applicationId
  }
  return ret
}

function scopeOf(tenantId: string | null | undefined): string {
  const tenant = tenants.value.find(t => t.id === tenantId)
  return tenantId ? `Tenant ${tenant?.name ?? tenantId}` : 'This application'
}

// the scope's own providers first, an email and a password ahead of the identity providers, then each tenant's together
function order(provider: SignInProvider): string {
  const tenantId = provider.configuration.tenantId
  return `${tenantId ? `1:${tenantId}` : '0'}:${provider.kind === 'password' ? '0' : '1'}`
}

function toRow(provider: SignInProvider): ConfigurationRow {
  let ret: ConfigurationRow
  if (provider.kind === 'password') {
    const configuration = provider.configuration
    ret = {
      id: configuration.id ?? '',
      source: provider,
      name: PASSWORD_PROVIDER_NAME,
      providerName: PASSWORD_PROVIDER_NAME,
      logo: null,
      providerAccount: null,
      scope: scopeOf(configuration.tenantId),
      enabled: configuration.enabled,
      status: configuration.enabled ? 'Enabled' : 'Disabled',
      secret: null,
      secretSeverity: 'info',
      created: configuration.created
    }
  } else {
    const configuration = provider.configuration
    ret = {
      id: configuration.id ?? '',
      source: provider,
      name: configuration.name,
      providerName: providerDisplayName(configuration.provider),
      logo: PROVIDER_LOGOS[configuration.provider as OidcProviderType] ?? null,
      providerAccount: configuration.providerAccount,
      scope: scopeOf(configuration.tenantId),
      enabled: configuration.enabled,
      status: configuration.enabled ? 'Enabled' : 'Disabled',
      secret: isManaged(configuration) ? 'Managed by Kinotic' : configuration.confidentialClient ? 'Secret held' : 'Public',
      secretSeverity: isManaged(configuration) || configuration.confidentialClient ? 'info' : 'warn',
      created: configuration.created
    }
  }
  return ret
}

// an organization holds few providers, so they are read in one page each and filtered to this scope here
async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const [oidc, passwords] = await Promise.all([
    Kinotic.ownedOidcConfigurations.findAll(Pageable.create(0, 1000)),
    Kinotic.ownedPasswordConfigurations.findAll(Pageable.create(0, 1000))
  ])
  const providers: SignInProvider[] = [
    ...(passwords.content ?? []).map(configuration => ({ kind: 'password' as const, configuration })),
    ...(oidc.content ?? []).map(configuration => ({ kind: 'oidc' as const, configuration }))
  ].filter(inScope)
  passwordScopes.value = providers.filter(provider => provider.kind === 'password').map(provider => provider.configuration.tenantId ?? null)
  let shown = providers.map(toRow).sort((a, b) => order(a.source).localeCompare(order(b.source)))
  if (searchText) {
    const needle = searchText.trim().toLowerCase()
    shown = shown.filter(row => [row.name, row.providerName, row.providerAccount, row.scope,
                                 row.source.kind === 'oidc' ? row.source.configuration.clientId : null]
        .some(field => (field ?? '').toLowerCase().includes(needle)))
  }
  const page: Page<DescriptiveIdentifiable> = { content: shown, totalElements: shown.length, cursor: undefined }
  return new FunctionalIterablePage(pageable, page, (next: Pageable) => load(next, searchText))
}

async function findCallbackUrl(id: string): Promise<string | null> {
  let ret: string | null = null
  try {
    ret = await Kinotic.ownedOidcConfigurations.findCallbackUrl(id)
  } catch (err) {
    showErrorToast(toast, 'Failed to load the callback URL', err)
  }
  return ret
}

function isManaged(configuration: OwnedOidcConfiguration): boolean {
  return configuration.clientRegistrationType === ClientRegistrationType.MANAGED
}

function facts(provider: SignInProvider) {
  const configuration = provider.configuration
  const offeredAt = props.applicationId === null ? [] : [{ label: 'Offered at', icon: Building2, value: scopeOf(configuration.tenantId) }]
  const created = { label: 'Created', icon: CalendarClock, value: configuration.created ? formatDate(configuration.created) : null }
  let ret
  if (provider.kind === 'password') {
    // only an application's own login signs people up with a password
    const signsUp = props.applicationId !== null && !provider.configuration.tenantId
    ret = [
      { label: 'Provider', icon: KeyRound, value: PASSWORD_PROVIDER_NAME },
      ...offeredAt,
      ...(signsUp ? [{
        label: 'New users', icon: UsersRound,
        value: provider.configuration.createUserOnSignIn ? 'Sign up with an email and a password' : 'Invited or created first'
      }] : []),
      created
    ]
  } else {
    const oidc = provider.configuration
    const own = isManaged(oidc) ? [] : [
      { label: 'Account', icon: Globe, value: oidc.providerAccount, mono: true },
      { label: 'Client ID', icon: Fingerprint, value: oidc.clientId, mono: true },
      { label: 'Issuer', icon: Globe, value: oidc.authority, mono: true },
      { label: 'Client', icon: KeyRound, value: oidc.confidentialClient ? 'Confidential, a secret is held' : 'Public, no secret is held' }
    ]
    ret = [
      { label: 'Provider', icon: KeyRound, value: providerDisplayName(oidc.provider) },
      ...offeredAt,
      ...(isManaged(oidc) ? [{ label: 'Credentials', icon: KeyRound, value: 'Managed by Kinotic' }] : own),
      ...(props.applicationId === null ? [] : [{
        label: 'New users', icon: UsersRound,
        value: oidc.createUserOnSignIn ? 'Sign up on first sign-in' : 'Invited or created first'
      }]),
      created
    ]
  }
  return ret
}

function openForm(provider: SignInProvider | null): void {
  editingOidc.value = provider?.kind === 'oidc' ? provider.configuration : null
  editingPassword.value = provider?.kind === 'password' ? provider.configuration : null
  formVisible.value = true
}

function onSaved(provider: SignInProvider): void {
  if (editingOidc.value && provider.kind === 'oidc') {
    editingOidc.value = provider.configuration
  }
  refreshTable()
}

function openCopyForm(configuration: OwnedOidcConfiguration): void {
  copySource.value = configuration
  copyApplicationId.value = null
  copied.value = null
  copiedCallbackUrl.value = null
  copyFormVisible.value = true
  if (APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications()
  }
}

async function submitCopy(): Promise<void> {
  if (copied.value) {
    copyFormVisible.value = false
    return
  }
  if (!copySource.value?.id || copyApplicationId.value === null) {
    return
  }
  copying.value = true
  try {
    copied.value = await Kinotic.ownedOidcConfigurations.copy(copySource.value.id, copyApplicationId.value)
    copiedCallbackUrl.value = await findCallbackUrl(copied.value.id!)
    refreshTable()
  } catch (err) {
    showErrorToast(toast, 'Failed to copy the provider', err, { life: 8000 })
  } finally {
    copying.value = false
  }
}

function rowActions(row: ConfigurationRow): MenuItem[] {
  const source = row.source
  return [
    { label: 'Edit', icon: 'pi pi-pencil', command: () => openForm(source) },
    ...(source.kind === 'oidc' && !isManaged(source.configuration)
        ? [{ label: 'Copy to application', icon: 'pi pi-copy', command: () => openCopyForm(source.configuration) }]
        : []),
    {
      label: row.enabled ? 'Disable' : 'Enable',
      icon: row.enabled ? 'pi pi-ban' : 'pi pi-check-circle',
      command: () => confirmToggleEnabled(row)
    },
    { label: 'Delete', icon: 'pi pi-trash', command: () => confirmDelete(row) }
  ]
}

function confirmToggleEnabled(row: ConfigurationRow): void {
  const disabling = row.enabled
  const source = row.source
  confirm.require({
    header: disabling ? 'Disable provider' : 'Enable provider',
    message: disabling
      ? `Disable ${row.name}? The login page stops offering it, and no one signs in through it until it is enabled again.`
      : `Enable ${row.name}? The login page offers it again.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: disabling ? 'Disable' : 'Enable', severity: disabling ? 'danger' : 'primary' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(async () => {
      if (source.kind === 'password') {
        await Kinotic.ownedPasswordConfigurations.save({ ...source.configuration, enabled: !disabling })
      } else {
        // saved without a secret, so the one held stays as it is
        await Kinotic.ownedOidcConfigurations.save({ ...source.configuration, enabled: !disabling }, null)
      }
    }, disabling ? 'Provider disabled' : 'Provider enabled', 'Failed to update the provider')
  })
}

function confirmDelete(row: ConfigurationRow): void {
  const source = row.source
  confirm.require({
    header: 'Delete provider',
    message: source.kind === 'password'
      ? `Delete ${row.name}? Sign-ins with a password stop at once.`
      : `Delete ${row.name}? Sign-ins through it stop at once, and the client secret held for it is deleted.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(async () => {
      const id = source.configuration.id!
      if (source.kind === 'password') {
        await Kinotic.ownedPasswordConfigurations.deleteById(id)
      } else {
        await Kinotic.ownedOidcConfigurations.deleteById(id)
      }
      removeRow(id)
      if (selectedRow.value?.id === row.id) {
        drawerVisible.value = false
      }
    }, 'Provider deleted', 'Failed to delete the provider')
  })
}
</script>
