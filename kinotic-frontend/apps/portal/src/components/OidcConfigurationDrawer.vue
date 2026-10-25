<template>
  <FormDrawer :visible="visible" :title="title" :description="description" @close="emit('update:visible', false)">
    <form id="oidc-configuration-form" @submit.prevent="submit">
      <!-- After a new configuration is saved: what the provider must be given before a sign-in through it succeeds -->
      <div v-if="saved" class="flex flex-col gap-5">
        <p class="text-sm text-surface-700 dark:text-surface-200">
          Add the callback URL below to the application registration at {{ providerDisplayName(saved.provider) }} as a redirect URI.
          Sign-ins through this provider fail until it is registered there.
        </p>
        <CopyableUrl label="Callback URL" :url="callbackUrl" />
        <CopyableUrl v-if="startUrl" label="Direct sign-in link" :url="startUrl" :help="START_URL_HELP" />
      </div>

      <!-- Which credentials the form asks for depends on the providers Kinotic manages, so the fields wait for the list -->
      <div v-else-if="managedProvidersLoaded" class="flex flex-col gap-5">
        <div>
          <label for="oidc-provider" class="mb-2 block text-sm font-medium">Provider</label>
          <Select v-if="isNew" input-id="oidc-provider" v-model="provider" :options="ORGANIZATION_OIDC_PROVIDERS" option-value="provider"
                  class="w-full">
            <template #value="{ value }">
              <span class="flex items-center gap-2.5">
                <img :src="PROVIDER_LOGOS[value as OidcProviderType]" alt="" class="h-4 w-4" />
                {{ providerDisplayName(value) }}
              </span>
            </template>
            <template #option="{ option }">
              <span class="flex items-center gap-2.5">
                <img :src="PROVIDER_LOGOS[option.provider as OidcProviderType]" alt="" class="h-4 w-4" />
                {{ providerDisplayName(option.provider) }}
              </span>
            </template>
          </Select>
          <div v-else class="flex items-center gap-2.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-2 text-sm dark:border-surface-700 dark:bg-surface-800/60">
            <img :src="PROVIDER_LOGOS[provider]" alt="" class="h-4 w-4" />
            <span class="flex-1">{{ providerDisplayName(provider) }}</span>
            <Lock :size="14" :stroke-width="1.75" class="text-surface-400" aria-hidden="true" />
          </div>
        </div>

        <div v-if="applicationId !== null">
          <label for="oidc-scope" class="mb-2 block text-sm font-medium">Offered at</label>
          <Select v-if="isNew" input-id="oidc-scope" v-model="tenantId" :options="scopeOptions" option-label="label" option-value="value"
                  :disabled="scopeOptions.length === 1" class="w-full" />
          <div v-else class="flex items-center gap-2.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-2 text-sm dark:border-surface-700 dark:bg-surface-800/60">
            <span class="flex-1">{{ scopeLabel(tenantId) }}</span>
            <Lock :size="14" :stroke-width="1.75" class="text-surface-400" aria-hidden="true" />
          </div>
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">The application's login, or the login of one of its tenants.</p>
        </div>

        <div v-if="managedOffered" role="radiogroup" aria-label="Credentials" class="flex flex-col gap-2.5">
          <span class="block text-sm font-medium">Credentials</span>
          <label v-for="option in REGISTRATION_OPTIONS" :key="option.value"
                 class="flex cursor-pointer items-start gap-3 rounded-xl border px-4 py-3.5"
                 :class="clientRegistrationType === option.value
                     ? 'border-primary bg-primary/5'
                     : 'border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-900'">
            <RadioButton v-model="clientRegistrationType" :input-id="`oidc-registration-${option.value}`" :value="option.value"
                         name="oidc-registration" class="mt-0.5 shrink-0" />
            <span class="flex flex-col gap-1">
              <span class="text-sm font-medium text-surface-950 dark:text-surface-0">{{ option.label }}</span>
              <span class="text-[0.8125rem] text-muted-color">{{ option.description }}</span>
            </span>
          </label>
        </div>

        <div>
          <label for="oidc-name" class="mb-2 block text-sm font-medium">Display name</label>
          <InputText id="oidc-name" v-model="name" placeholder="Acme Okta" autocomplete="off" class="w-full" />
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">What the login page prints on the provider's button.</p>
        </div>

        <template v-if="!managed">
        <div v-if="offered?.providerAccountLabel">
          <label for="oidc-account" class="mb-2 block text-sm font-medium">{{ offered.providerAccountLabel }}</label>
          <InputText id="oidc-account" v-model="providerAccount" :placeholder="ACCOUNT_PLACEHOLDERS[provider]" autocomplete="off"
                     :invalid="accountInvalid" class="w-full font-mono" />
          <p v-if="accountInvalid" class="mt-1.5 text-[0.8125rem] text-red-600 dark:text-red-400">
            Enter the {{ offered.providerAccountLabel }} in the form {{ ACCOUNT_PLACEHOLDERS[provider] }}{{ everyDirectoryAccounts ? `, or ${everyDirectoryAccounts}` : '' }}.
          </p>
          <p v-else-if="everyDirectoryAccounts" class="mt-1.5 text-[0.8125rem] text-muted-color">
            Or {{ everyDirectoryAccounts }}, which admit the users of every directory.
          </p>
        </div>

        <div>
          <label for="oidc-client-id" class="mb-2 block text-sm font-medium">Client ID</label>
          <InputText id="oidc-client-id" v-model="clientId" autocomplete="off" class="w-full font-mono" />
        </div>

        <div>
          <label for="oidc-client-secret" class="mb-2 block text-sm font-medium">Client secret</label>
          <Password input-id="oidc-client-secret" v-model="clientSecret" :feedback="false" toggle-mask :disabled="choosingSecret && publicClient"
                    :placeholder="secretPlaceholder" :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
          <div v-if="choosingSecret" class="mt-2.5 flex items-center gap-2">
            <Checkbox input-id="oidc-public-client" v-model="publicClient" binary />
            <label for="oidc-public-client" class="text-sm">Public client (no secret)</label>
          </div>
          <div v-else-if="configuration?.confidentialClient" class="mt-1.5 flex items-center justify-between gap-3">
            <p class="text-[0.8125rem] text-muted-color">Leave it empty to keep the secret held for this provider.</p>
            <Button type="button" label="Make this a public client" severity="danger" text size="small" :loading="removingSecret"
                    @click="confirmMakePublic" />
          </div>
        </div>
        </template>

        <div v-if="applicationId !== null" class="flex items-start gap-3 rounded-xl border border-surface-200 bg-surface-0 px-4 py-3.5 dark:border-surface-700 dark:bg-surface-900">
          <ToggleSwitch input-id="oidc-create-users" v-model="createUserOnSignIn" class="mt-0.5 shrink-0" />
          <div class="flex flex-col gap-1">
            <label for="oidc-create-users" class="text-sm font-medium text-surface-950 dark:text-surface-0">Let new people sign up</label>
            <p class="text-[0.8125rem] text-muted-color">
              {{ createUserOnSignIn
                  ? 'Someone the provider verifies who is not yet a user here becomes one the first time they sign in, where the system manages the application\'s tenants or at the tenant\'s own login.'
                  : 'Someone the provider verifies who is not yet a user here is refused until they are invited or created.' }}
            </p>
          </div>
        </div>

        <div class="flex items-start gap-3 rounded-xl border border-surface-200 bg-surface-0 px-4 py-3.5 dark:border-surface-700 dark:bg-surface-900">
          <ToggleSwitch input-id="oidc-enabled" v-model="enabled" class="mt-0.5 shrink-0" />
          <div class="flex flex-col gap-1">
            <label for="oidc-enabled" class="text-sm font-medium text-surface-950 dark:text-surface-0">Enabled</label>
            <p class="text-[0.8125rem] text-muted-color">
              {{ enabled ? 'The login page offers this provider.' : 'The login page does not offer this provider, and no one signs in through it.' }}
            </p>
          </div>
        </div>

        <div v-if="!managed">
          <button type="button" class="flex items-center gap-1.5 text-sm font-medium text-surface-700 hover:text-surface-950 dark:text-surface-200 dark:hover:text-surface-0"
                  :aria-expanded="showAdvanced" @click="showAdvanced = !showAdvanced">
            <ChevronRight :size="16" :stroke-width="1.75" :class="['transition-transform', showAdvanced ? 'rotate-90' : '']" aria-hidden="true" />
            Advanced
          </button>
          <div v-if="showAdvanced" class="mt-3">
            <label for="oidc-audience" class="mb-2 flex items-center justify-between text-sm font-medium">
              Audience
              <span class="text-xs font-normal text-muted-color">Optional</span>
            </label>
            <InputText id="oidc-audience" v-model="audience" autocomplete="off" class="w-full font-mono" />
            <p class="mt-1.5 text-[0.8125rem] text-muted-color">The aud claim the provider's tokens carry, when it is not the client ID.</p>
          </div>
        </div>

        <template v-if="!isNew && !managed">
          <CopyableUrl label="Callback URL" :url="callbackUrl" help="The redirect URI registered at the provider." />
          <CopyableUrl v-if="startUrl" label="Direct sign-in link" :url="startUrl" :help="START_URL_HELP" />
        </template>
      </div>
    </form>

    <template #footer>
      <Button v-if="saved" type="button" label="Done" @click="emit('update:visible', false)" />
      <template v-else>
        <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
        <Button type="submit" form="oidc-configuration-form" :label="isNew ? 'Add provider' : 'Save'" :loading="saving" :disabled="!complete" />
      </template>
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import RadioButton from 'primevue/radiobutton'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ChevronRight, Lock } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import {
  ClientRegistrationType,
  OidcConfigurationType,
  OidcProviderType,
  ORGANIZATION_OIDC_PROVIDERS,
  admitsEveryDirectory,
  findOrganizationOidcProvider,
  isProviderAccountValid,
  type OwnedOidcConfiguration,
  type Tenant
} from '@kinotic-ai/management-api'
import { FormDrawer, showErrorToast } from '@kinotic-ai/frontend-common'
import CopyableUrl from '@/components/CopyableUrl.vue'
import { PROVIDER_LOGOS, START_URL_HELP, startUrlOf } from '@/util/oidcConfigurations'
import { providerDisplayName } from '@/components/SocialAuthButton.vue'

/**
 * The form an organization adds or edits one of its own OIDC configurations with: of the organization's login when
 * applicationId is null, otherwise of the application's login or one of its tenants'. A new configuration's
 * callback URL is shown once it is saved; an edited one's alongside its fields. The held client secret is kept
 * unless a new one is entered, and removed only through the confirmed "Make this a public client". An application's
 * configuration of a kind Kinotic manages a registration for chooses between Kinotic's registration, which needs no
 * client, account or secret, and the organization's own; the fields of its own are kept while Kinotic's is used.
 */
const props = defineProps<{
  visible: boolean
  /** The configuration to edit, or null to add one. */
  configuration: OwnedOidcConfiguration | null
  applicationId: string | null
  /** The application's tenants, any of whose logins a new configuration may be offered at. */
  tenants: Tenant[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', configuration: OwnedOidcConfiguration): void
}>()

const ACCOUNT_PLACEHOLDERS: Partial<Record<OidcProviderType, string>> = {
  [OidcProviderType.AZURE_AD]: '00000000-0000-0000-0000-000000000000',
  [OidcProviderType.OKTA]: 'acme.okta.com'
}

const REGISTRATION_OPTIONS = [
  {
    value: ClientRegistrationType.MANAGED,
    label: 'Managed by Kinotic',
    description: 'Works with no setup. The provider\'s consent screen shows Kinotic\'s name.'
  },
  {
    value: ClientRegistrationType.OWN,
    label: 'Your own credentials',
    description: 'Your client ID and secret, so the consent screen shows your application\'s name and branding. Users who signed in either way stay the same users.'
  }
]

const toast = useToast()
const confirm = useConfirm()

const provider = ref<OidcProviderType>(OidcProviderType.OKTA)
const tenantId = ref<string | null>(null)
const name = ref('')
const providerAccount = ref('')
const clientId = ref('')
const clientSecret = ref('')
const publicClient = ref(false)
const audience = ref('')
const enabled = ref(true)
const createUserOnSignIn = ref(false)
const clientRegistrationType = ref<ClientRegistrationType>(ClientRegistrationType.OWN)
/** The provider kinds Kinotic manages a registration for, which an application's own login may sign in through. */
const managedProviders = ref<OidcProviderType[]>([])
const managedProvidersLoaded = ref(false)
const showAdvanced = ref(false)
const saving = ref(false)
const removingSecret = ref(false)
/** The new configuration once saved, which switches the drawer to its callback URL. */
const saved = ref<OwnedOidcConfiguration | null>(null)
const callbackUrl = ref<string | null>(null)

const isNew = computed(() => props.configuration === null)
// only an application's own login, not a tenant's, signs in through Kinotic's registration; a configuration already
// using it keeps the choice while the list loads, so a save never turns it into its own client unasked
const managedOffered = computed(() => props.applicationId !== null && tenantId.value === null
    && (managedProviders.value.includes(provider.value) || props.configuration?.clientRegistrationType === ClientRegistrationType.MANAGED))
const managed = computed(() => managedOffered.value && clientRegistrationType.value === ClientRegistrationType.MANAGED)
// a client of its own gets a secret, or is declared public, when it is added and when a configuration that holds none
// stops using Kinotic's registration
const choosingSecret = computed(() => !managed.value && (isNew.value
    || (props.configuration?.clientRegistrationType === ClientRegistrationType.MANAGED && !props.configuration.confidentialClient)))
const offered = computed(() => findOrganizationOidcProvider(provider.value))
/** The accounts admitting every directory that an application's or a tenant's login may name, listed for the form. */
const everyDirectoryAccounts = computed(() => props.applicationId !== null && offered.value?.everyDirectoryAccounts.length
    ? offered.value.everyDirectoryAccounts.join(' or ')
    : null)
const accountInvalid = computed(() => offered.value?.providerAccountLabel != null && providerAccount.value.trim() !== ''
    && !isAccountAccepted(providerAccount.value.trim()))
const complete = computed(() => name.value.trim() !== '' && (managed.value || (clientId.value.trim() !== ''
    && isAccountAccepted(accountValue())
    && (!choosingSecret.value || publicClient.value || clientSecret.value !== ''))))

const title = computed(() => saved.value ? 'Provider added' : isNew.value ? 'Add provider' : 'Edit provider')
const description = computed(() => {
  let ret: string
  if (saved.value) {
    ret = `${saved.value.name} is saved.`
  } else if (props.applicationId === null) {
    ret = 'An identity provider your organization\'s members sign in to the portal with.'
  } else {
    ret = 'An identity provider this application\'s users sign in with.'
  }
  return ret
})
const secretPlaceholder = computed(() => {
  let ret: string
  if (choosingSecret.value) {
    ret = publicClient.value ? 'No secret' : 'The secret the provider issued'
  } else if (props.configuration?.confidentialClient) {
    ret = 'A secret is held'
  } else {
    ret = 'None held; enter one to send it'
  }
  return ret
})
const scopeOptions = computed(() => [
  { label: 'This application', value: null as string | null },
  ...props.tenants.map(tenant => ({ label: scopeLabel(tenant.id), value: tenant.id }))
])
const startUrl = computed(() => {
  const shown = saved.value ?? props.configuration
  return shown ? startUrlOf(shown) : null
})

watch(() => props.visible, visible => {
  if (visible) {
    reset()
  }
})

function scopeLabel(id: string | null): string {
  const tenant = props.tenants.find(t => t.id === id)
  return id === null ? 'This application' : `Tenant ${tenant?.name ?? id}`
}

function accountValue(): string | null {
  return offered.value?.providerAccountLabel ? providerAccount.value.trim() : null
}

// an organization's own login names one directory of its own, which the server requires of every save
function isAccountAccepted(account: string | null): boolean {
  return isProviderAccountValid(provider.value, account)
      && (props.applicationId !== null || !admitsEveryDirectory(provider.value, account))
}

function reset(): void {
  const editing = props.configuration
  provider.value = editing?.provider ?? (props.applicationId !== null ? OidcProviderType.GOOGLE : OidcProviderType.OKTA)
  tenantId.value = editing?.tenantId ?? null
  name.value = editing?.name ?? ''
  providerAccount.value = editing?.providerAccount ?? ''
  clientId.value = editing?.clientId ?? ''
  clientSecret.value = ''
  publicClient.value = false
  audience.value = editing?.audience ?? ''
  enabled.value = editing?.enabled ?? true
  createUserOnSignIn.value = editing?.createUserOnSignIn ?? false
  clientRegistrationType.value = editing?.clientRegistrationType ?? (editing ? ClientRegistrationType.OWN : ClientRegistrationType.MANAGED)
  showAdvanced.value = (editing?.audience ?? '') !== ''
  saved.value = null
  callbackUrl.value = null
  if (editing?.id) {
    loadCallbackUrl(editing.id)
  }
  // an organization's own login never signs in through Kinotic's registration, so its form needs no list
  managedProvidersLoaded.value = props.applicationId === null
  if (props.applicationId !== null) {
    loadManagedProviders()
  }
}

async function loadManagedProviders(): Promise<void> {
  try {
    managedProviders.value = await Kinotic.ownedOidcConfigurations.findManagedProviders()
  } catch (err) {
    showErrorToast(toast, 'Failed to load the providers Kinotic manages', err)
  } finally {
    managedProvidersLoaded.value = true
  }
}

async function loadCallbackUrl(id: string): Promise<void> {
  try {
    callbackUrl.value = await Kinotic.ownedOidcConfigurations.findCallbackUrl(id)
  } catch (err) {
    showErrorToast(toast, 'Failed to load the callback URL', err)
  }
}

function configurationToSave(): OwnedOidcConfiguration {
  const fields = {
    name: name.value.trim(),
    // the fields of the organization's own registration are kept while Kinotic's is used, for a switch back
    providerAccount: accountValue() || null,
    clientId: clientId.value.trim() || null,
    audience: audience.value.trim() || null,
    enabled: enabled.value,
    createUserOnSignIn: props.applicationId !== null && createUserOnSignIn.value,
    ...(managedOffered.value || props.configuration?.clientRegistrationType
        ? { clientRegistrationType: managedOffered.value ? clientRegistrationType.value : ClientRegistrationType.OWN }
        : {})
  }
  let ret: OwnedOidcConfiguration
  if (props.configuration) {
    ret = { ...props.configuration, ...fields }
  } else {
    ret = {
      ...fields,
      id: null,
      type: props.applicationId === null
          ? OidcConfigurationType.ORGANIZATION
          : tenantId.value === null ? OidcConfigurationType.APPLICATION : OidcConfigurationType.TENANT,
      organizationId: null,
      applicationId: props.applicationId,
      tenantId: props.applicationId === null ? null : tenantId.value,
      provider: provider.value,
      authority: null,
      confidentialClient: false,
      created: null,
      updated: null
    }
  }
  return ret
}

async function submit(): Promise<void> {
  if (saved.value) {
    emit('update:visible', false)
    return
  }
  if (!complete.value) {
    return
  }
  saving.value = true
  try {
    const secret = managed.value || (choosingSecret.value && publicClient.value) ? null : clientSecret.value || null
    const result = await Kinotic.ownedOidcConfigurations.save(configurationToSave(), secret)
    emit('saved', result)
    // Kinotic's registration needs nothing registered at the provider, so there is no callback URL to show
    if (isNew.value && !managed.value) {
      saved.value = result
      await loadCallbackUrl(result.id!)
    } else {
      toast.add({ severity: 'success', summary: 'Provider saved', life: 4000 })
      emit('update:visible', false)
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to save the provider', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}

function confirmMakePublic(): void {
  const editing = props.configuration
  if (!editing?.id) {
    return
  }
  confirm.require({
    header: 'Make this a public client',
    message: `Delete the client secret held for ${editing.name}? Sign-ins through it send the provider no secret from then on, which the provider refuses unless its registration is a public client.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete secret', severity: 'danger' },
    rejectProps: { label: 'Keep it', severity: 'secondary', outlined: true },
    accept: async () => {
      removingSecret.value = true
      try {
        const result = await Kinotic.ownedOidcConfigurations.deleteClientSecret(editing.id!)
        if (result) {
          emit('saved', result)
        }
        toast.add({ severity: 'success', summary: 'Client secret deleted', life: 4000 })
      } catch (err) {
        showErrorToast(toast, 'Failed to delete the client secret', err, { life: 8000 })
      } finally {
        removingSecret.value = false
      }
    }
  })
}
</script>
