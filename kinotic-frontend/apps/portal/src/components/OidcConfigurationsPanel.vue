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
          <img :src="PROVIDER_LOGOS[item.configuration.provider as OidcProviderType]" alt="" class="h-4 w-4 shrink-0" />
          {{ providerDisplayName(item.configuration.provider) }}
        </span>
      </template>

      <template #item.providerAccount="{ item }">
        {{ item.providerAccount || '—' }}
      </template>

      <template #item.status="{ item }">
        <Tag :value="item.status" :severity="item.configuration.enabled ? 'success' : 'secondary'" />
      </template>

      <template #item.secret="{ item }">
        <Tag :value="item.secret" :severity="isManaged(item.configuration) || item.configuration.confidentialClient ? 'info' : 'warn'" />
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
        <FactList :facts="facts(selectedRow.configuration)" />
        <CopyableUrl v-if="!isManaged(selectedRow.configuration)" label="Callback URL" :url="selectedCallbackUrl"
                     help="Register this as a redirect URI at the provider; sign-ins through this provider fail until it is." />
        <CopyableUrl v-if="startUrlOf(selectedRow.configuration)" label="Direct sign-in link" :url="startUrlOf(selectedRow.configuration)"
                     :help="START_URL_HELP" />
        <div>
          <Button label="Edit" icon="pi pi-pencil" severity="secondary" outlined @click="openForm(selectedRow.configuration)" />
        </div>
      </div>
    </SteppingDrawer>

    <OidcConfigurationDrawer v-model:visible="formVisible" :configuration="editing" :application-id="applicationId" :tenants="tenants"
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
import OidcConfigurationDrawer from '@/components/OidcConfigurationDrawer.vue'
import { providerDisplayName } from '@/components/SocialAuthButton.vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { PROVIDER_LOGOS, START_URL_HELP, startUrlOf } from '@/util/oidcConfigurations'

/** One table row: an organization's own OIDC configuration and the columns it is listed by. */
interface ConfigurationRow extends DescriptiveIdentifiable {
  configuration: OwnedOidcConfiguration
  name: string
  providerAccount: string | null
  scope: string
  status: string
  secret: string
  created: string | null
}

/**
 * The organization's own OIDC configurations of one sign-in scope, listed with what a provider is given, and added,
 * edited, copied, enabled, disabled and deleted from here: the organization's login when applicationId is null,
 * otherwise the application's login and its tenants', grouped by tenant.
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
const formVisible = ref(false)
const editing = ref<OwnedOidcConfiguration | null>(null)
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
    ? 'The identity providers your organization\'s members sign in to the portal with, such as your Okta or Microsoft Entra ID directory. Each one is offered on your organization\'s login page once its callback URL is registered at the provider.'
    : 'The identity providers this application\'s users sign in and sign up with, offered on the application\'s login page, or on one tenant\'s for a provider of that tenant. Google and Microsoft can be managed by Kinotic, which works with no setup; a provider with your own credentials works once its callback URL is registered at the provider.')
const emptyStateText = computed(() => props.applicationId === null
    ? 'No providers yet. Add one so your members sign in with your organization\'s own accounts.'
    : 'No providers yet. Add one so this application\'s users sign in with an identity provider.')

// every application but the one the source is offered at
const copyTargets = computed(() => APPLICATION_STATE.allApplications.filter(application => application.id !== props.applicationId))
const copyDescription = computed(() => copySource.value ? `Offer ${copySource.value.name} at another application's login.` : undefined)

watch(() => props.applicationId, loadTenants, { immediate: true })

watch(selectedRow, async row => {
  selectedCallbackUrl.value = null
  if (row?.configuration.id) {
    selectedCallbackUrl.value = await findCallbackUrl(row.configuration.id)
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

function inScope(configuration: OwnedOidcConfiguration): boolean {
  return props.applicationId === null
      ? configuration.type === OidcConfigurationType.ORGANIZATION
      : configuration.type !== OidcConfigurationType.ORGANIZATION && configuration.applicationId === props.applicationId
}

function scopeOf(configuration: OwnedOidcConfiguration): string {
  const tenant = tenants.value.find(t => t.id === configuration.tenantId)
  return configuration.tenantId ? `Tenant ${tenant?.name ?? configuration.tenantId}` : 'This application'
}

// the application's own configurations first, then each tenant's together
function scopeOrder(configuration: OwnedOidcConfiguration): string {
  return configuration.tenantId ? `1:${configuration.tenantId}` : '0'
}

function toRow(configuration: OwnedOidcConfiguration): ConfigurationRow {
  return {
    id: configuration.id ?? '',
    configuration,
    name: configuration.name,
    providerAccount: configuration.providerAccount,
    scope: scopeOf(configuration),
    status: configuration.enabled ? 'Enabled' : 'Disabled',
    secret: isManaged(configuration) ? 'Managed by Kinotic' : configuration.confidentialClient ? 'Secret held' : 'Public',
    created: configuration.created
  }
}

// an organization holds few configurations, so they are read in one page and filtered to this scope here
async function load(pageable: Pageable, searchText: string | null): Promise<IterablePage<DescriptiveIdentifiable>> {
  const all = await Kinotic.ownedOidcConfigurations.findAll(Pageable.create(0, 1000))
  let shown = (all.content ?? []).filter(inScope)
  shown.sort((a, b) => scopeOrder(a).localeCompare(scopeOrder(b)))
  if (searchText) {
    const needle = searchText.trim().toLowerCase()
    shown = shown.filter(c => [c.name, c.providerAccount, c.clientId, scopeOf(c)].some(field => (field ?? '').toLowerCase().includes(needle)))
  }
  const page: Page<DescriptiveIdentifiable> = { content: shown.map(toRow), totalElements: shown.length, cursor: undefined }
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

function facts(configuration: OwnedOidcConfiguration) {
  const own = isManaged(configuration) ? [] : [
    { label: 'Account', icon: Globe, value: configuration.providerAccount, mono: true },
    { label: 'Client ID', icon: Fingerprint, value: configuration.clientId, mono: true },
    { label: 'Issuer', icon: Globe, value: configuration.authority, mono: true },
    { label: 'Client', icon: KeyRound, value: configuration.confidentialClient ? 'Confidential, a secret is held' : 'Public, no secret is held' }
  ]
  return [
    { label: 'Provider', icon: KeyRound, value: providerDisplayName(configuration.provider) },
    ...(props.applicationId === null ? [] : [{ label: 'Offered at', icon: Building2, value: scopeOf(configuration) }]),
    ...(isManaged(configuration) ? [{ label: 'Credentials', icon: KeyRound, value: 'Managed by Kinotic' }] : own),
    ...(props.applicationId === null ? [] : [{
      label: 'New users', icon: UsersRound,
      value: configuration.createUserOnSignIn ? 'Sign up on first sign-in' : 'Invited or created first'
    }]),
    { label: 'Created', icon: CalendarClock, value: configuration.created ? formatDate(configuration.created) : null }
  ]
}

function openForm(configuration: OwnedOidcConfiguration | null): void {
  editing.value = configuration
  formVisible.value = true
}

function onSaved(configuration: OwnedOidcConfiguration): void {
  if (editing.value) {
    editing.value = configuration
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
  const configuration = row.configuration
  return [
    { label: 'Edit', icon: 'pi pi-pencil', command: () => openForm(configuration) },
    ...(isManaged(configuration) ? [] : [{ label: 'Copy to application', icon: 'pi pi-copy', command: () => openCopyForm(configuration) }]),
    {
      label: configuration.enabled ? 'Disable' : 'Enable',
      icon: configuration.enabled ? 'pi pi-ban' : 'pi pi-check-circle',
      command: () => confirmToggleEnabled(configuration)
    },
    { label: 'Delete', icon: 'pi pi-trash', command: () => confirmDelete(configuration) }
  ]
}

function confirmToggleEnabled(configuration: OwnedOidcConfiguration): void {
  const disabling = configuration.enabled
  confirm.require({
    header: disabling ? 'Disable provider' : 'Enable provider',
    message: disabling
      ? `Disable ${configuration.name}? The login page stops offering it, and no one signs in through it until it is enabled again.`
      : `Enable ${configuration.name}? The login page offers it again.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: disabling ? 'Disable' : 'Enable', severity: disabling ? 'danger' : 'primary' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    // saved without a secret, so the one held stays as it is
    accept: () => run(async () => {
      await Kinotic.ownedOidcConfigurations.save({ ...configuration, enabled: !disabling }, null)
    }, disabling ? 'Provider disabled' : 'Provider enabled', 'Failed to update the provider')
  })
}

function confirmDelete(configuration: OwnedOidcConfiguration): void {
  confirm.require({
    header: 'Delete provider',
    message: `Delete ${configuration.name}? Sign-ins through it stop at once, and the client secret held for it is deleted.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(async () => {
      await Kinotic.ownedOidcConfigurations.deleteById(configuration.id!)
      removeRow(configuration.id!)
      if (selectedRow.value?.id === configuration.id) {
        drawerVisible.value = false
      }
    }, 'Provider deleted', 'Failed to delete the provider')
  })
}
</script>
