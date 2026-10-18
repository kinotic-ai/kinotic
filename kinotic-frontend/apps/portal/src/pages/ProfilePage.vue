<template>
  <div class="max-w-[860px]">
    <PageHeader title="Profile" description="How you appear to everyone else in Kinotic." />

    <div class="flex flex-col gap-6">
      <section class="relative overflow-hidden rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="pointer-events-none absolute -right-20 -top-28 h-72 w-72 rounded-full bg-primary-500/10 blur-[90px] dark:bg-primary-500/20"
             aria-hidden="true" />

        <div class="relative flex flex-wrap items-center gap-5 px-6 py-6">
          <Skeleton v-if="loading" shape="circle" size="4.5rem" />
          <div v-else aria-hidden="true"
               class="grid h-18 w-18 shrink-0 place-items-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600
                      text-2xl font-semibold text-white shadow-lg shadow-primary-500/30 ring-4 ring-primary-500/10">
            {{ initials || '?' }}
          </div>

          <div class="flex min-w-0 flex-1 flex-col gap-1.5">
            <Skeleton v-if="loading" height="1.75rem" width="12rem" />
            <h2 v-else class="truncate text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0">
              {{ savedDisplayName || 'Unnamed user' }}
            </h2>

            <Skeleton v-if="loading" height="1rem" width="16rem" />
            <span v-else class="truncate text-sm text-muted-color">{{ email }}</span>
          </div>

          <Tag v-if="!loading" :value="statusLabel" :severity="statusSeverity(statusLabel)" rounded />
        </div>

        <dl class="relative grid gap-5 border-t border-surface-200 px-6 py-5 sm:grid-cols-3 dark:border-surface-700">
          <div v-for="detail in details" :key="detail.label" class="flex min-w-0 flex-col gap-1.5">
            <dt class="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-color">
              <i :class="detail.icon" />
              {{ detail.label }}
            </dt>
            <dd class="m-0 min-w-0">
              <Skeleton v-if="loading" height="1rem" width="7rem" />
              <button v-else-if="detail.copyable" type="button"
                      class="group flex w-full min-w-0 items-center gap-2 text-left font-mono text-xs text-surface-700 dark:text-surface-300"
                      v-tooltip.top="detail.value" :aria-label="`Copy ${detail.label.toLowerCase()}`"
                      @click="copy(detail.label, detail.value)">
                <span class="truncate">{{ detail.value }}</span>
                <i :class="[copied === detail.label ? 'pi pi-check text-green-500' : 'pi pi-copy opacity-50 group-hover:opacity-100',
                            'shrink-0 !text-xs transition-opacity']" />
              </button>
              <span v-else class="text-sm">{{ detail.value }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section class="rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-4 px-6 py-6">
          <div class="flex flex-col gap-1">
            <label for="profile-display-name" class="text-sm font-medium">Display name</label>
            <small class="text-muted-color">Shown wherever you appear in the platform.</small>
          </div>

          <Skeleton v-if="loading" height="2.5rem" />
          <InputText v-else id="profile-display-name" v-model="displayName" :invalid="emptyName"
                     class="max-w-[26rem]" autocomplete="name" maxlength="80"
                     @keyup.enter="save" @keyup.esc="reset" />

          <small v-if="emptyName" class="text-red-500">A display name is required.</small>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-surface-200 px-6 py-4 dark:border-surface-700">
          <span class="flex items-center gap-2 text-xs text-muted-color">
            <template v-if="dirty">
              <span class="h-1.5 w-1.5 rounded-full bg-amber-500" aria-hidden="true" />
              Unsaved changes
            </template>
          </span>

          <div class="flex items-center gap-2">
            <Button v-if="dirty" label="Reset" severity="secondary" text size="small"
                    :disabled="saving" @click="reset" />
            <Button label="Save changes" size="small" :loading="saving" :disabled="!canSave" @click="save" />
          </div>
        </div>
      </section>

      <!-- One account, several ways in: the password and every provider identity bound to it. The last
           remaining method cannot be removed, so the account always keeps a way to sign in. -->
      <section class="rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-1 px-6 pt-6">
          <h3 class="text-sm font-medium">Sign-in methods</h3>
          <small class="text-muted-color">How you sign in to Kinotic, whichever organization you open.</small>
        </div>

        <ul class="mt-4 border-t border-surface-200 dark:border-surface-700">
          <li v-for="method in methods" :key="method.id"
              class="flex flex-wrap items-center gap-3 border-b border-surface-100 px-6 py-3.5 dark:border-surface-800">
            <component :is="method.kind === SignInMethodKind.PASSWORD ? KeyRound : ShieldCheck" :size="18" :stroke-width="1.75"
                       class="shrink-0 text-surface-600 dark:text-surface-300" aria-hidden="true" />
            <div class="flex min-w-0 flex-1 flex-col">
              <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ method.name }}</span>
              <span v-if="method.created" class="text-xs text-muted-color">Connected {{ DatetimeUtil.formatMonthDayYear(method.created) }}</span>
            </div>
            <Button v-if="method.kind === SignInMethodKind.PASSWORD" label="Change password" size="small" severity="secondary" outlined
                    @click="openPasswordDialog" />
            <Button v-else label="Remove" size="small" severity="secondary" text :disabled="methods.length < 2"
                    v-tooltip.top="methods.length < 2 ? 'Add another sign-in method first' : undefined"
                    @click="removeMethod(method)" />
          </li>
          <li v-if="!hasPassword" class="flex flex-wrap items-center gap-3 border-b border-surface-100 px-6 py-3.5 dark:border-surface-800">
            <KeyRound :size="18" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
            <span class="flex-1 text-sm text-muted-color">No password set</span>
            <Button label="Set password" size="small" severity="secondary" outlined @click="openPasswordDialog" />
          </li>
        </ul>

        <div v-if="connectableProviders.length > 0" class="flex flex-wrap items-center gap-3 px-6 py-4">
          <span class="text-xs text-muted-color">Connect another way to sign in:</span>
          <SocialAuthButton v-for="provider in connectableProviders" :key="provider.configurationId" :provider="provider.provider"
                            :name="provider.type === OidcConfigurationType.ORG_SIGNUP ? undefined : provider.name"
                            :action="apiUrl('/api/auth/account/methods/start/' + encodeURIComponent(provider.configurationId))" intent="connect" />
        </div>
      </section>

      <!-- The organizations the account is a member of; switching replaces the page's login and reloads -->
      <section class="rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-1 px-6 pt-6">
          <h3 class="text-sm font-medium">Organizations</h3>
          <small class="text-muted-color">The organizations you belong to. You are signed in to one at a time.</small>
        </div>

        <ul class="mt-4 border-t border-surface-200 dark:border-surface-700">
          <li v-for="organization in organizations" :key="organization.id ?? ''"
              class="flex flex-wrap items-center gap-3 border-b border-surface-100 px-6 py-3.5 dark:border-surface-800">
            <Building2 :size="18" :stroke-width="1.75" class="shrink-0 text-surface-600 dark:text-surface-300" aria-hidden="true" />
            <div class="flex min-w-0 flex-1 flex-col">
              <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ organization.name }}</span>
              <span class="font-mono text-xs text-muted-color">{{ organization.id }}</span>
            </div>
            <Tag v-if="organization.id === currentOrganizationId" value="Current" severity="success" rounded />
            <Button v-else label="Switch" size="small" severity="secondary" outlined :loading="switchingTo === organization.id"
                    @click="switchTo(organization.id ?? '')" />
          </li>
        </ul>
      </section>
    </div>

    <FormDialog v-model:visible="passwordDialogVisible" :icon="KeyRound"
                :title="hasPassword ? 'Change password' : 'Set a password'"
                :description="hasPassword ? 'Enter your current password, then the new one.' : 'A password lets you sign in without a provider.'"
                @submit="savePassword">
      <div class="flex flex-col gap-4">
        <div v-if="hasPassword">
          <label for="current-password" class="mb-2 block text-sm font-medium">Current password</label>
          <Password id="current-password" v-model="currentPassword" :feedback="false" toggleMask class="w-full" input-class="w-full" autofocus />
        </div>
        <div>
          <label for="new-password" class="mb-2 block text-sm font-medium">New password</label>
          <Password id="new-password" v-model="newPassword" :feedback="false" toggleMask class="w-full" input-class="w-full" />
        </div>
        <div>
          <label for="confirm-password" class="mb-2 block text-sm font-medium">Confirm new password</label>
          <Password id="confirm-password" v-model="confirmPassword" :feedback="false" toggleMask class="w-full" input-class="w-full"
                    :invalid="confirmPassword !== '' && confirmPassword !== newPassword" />
        </div>
      </div>
      <template #footer>
        <Button type="button" label="Cancel" severity="secondary" outlined @click="passwordDialogVisible = false" />
        <Button type="submit" :label="hasPassword ? 'Change password' : 'Set password'" :loading="savingPassword" :disabled="!canSavePassword" />
      </template>
    </FormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { Building2, KeyRound, ShieldCheck } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type LoginProviderSummary, OidcConfigurationType, type Organization, PASSWORD_SIGN_IN_METHOD_ID, type SignInMethod, SignInMethodKind } from '@kinotic-ai/management-api'
import { FormDialog, apiUrl } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
import { USER_STATE } from '@/states/IUserState'

import { DatetimeUtil, showErrorToast, statusSeverity } from '@kinotic-ai/frontend-common'
import { PageHeader } from '@kinotic-ai/frontend-common'
import { PROFILE_STATE } from '@/states/IProfileState'

/** One read-only fact about the account, rendered as a column of the identity card's footer. */
interface ProfileDetail {
  label: string
  value: string
  icon: string
  copyable?: boolean
}

/** How long the copy button shows its confirmation before returning to the copy icon. */
const COPIED_FEEDBACK_MILLIS = 2000

/** The platform registrations an account may connect as a sign-in method; any other provider offered is omitted. */

const toast = useToast()
const route = useRoute()
const router = useRouter()

const methods = ref<SignInMethod[]>([])
const connectableProviders = ref<LoginProviderSummary[]>([])
const organizations = ref<Organization[]>([])
const switchingTo = ref<string | null>(null)

const passwordDialogVisible = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const savingPassword = ref(false)

const hasPassword = computed(() => methods.value.some(method => method.id === PASSWORD_SIGN_IN_METHOD_ID))
const currentOrganizationId = computed(() => USER_STATE.getOrganizationId())
const canSavePassword = computed(() => newPassword.value.length > 0 && newPassword.value === confirmPassword.value
    && (!hasPassword.value || currentPassword.value.length > 0))

const displayName = ref('')
const loading = ref(true)
const saving = ref(false)
const copied = ref<string | null>(null)

let copiedTimer: ReturnType<typeof setTimeout> | null = null

const email = computed(() => PROFILE_STATE.profile?.email ?? '')
const savedDisplayName = computed(() => PROFILE_STATE.profile?.displayName ?? '')
const initials = computed(() => PROFILE_STATE.initials)
const statusLabel = computed(() => PROFILE_STATE.profile?.enabled ? 'Active' : 'Disabled')

const dirty = computed(() => !loading.value && displayName.value.trim() !== savedDisplayName.value)
const emptyName = computed(() => !loading.value && displayName.value.trim().length === 0)
const canSave = computed(() => dirty.value && !emptyName.value)

const details = computed<ProfileDetail[]>(() => [
  { label: 'Member since', value: DatetimeUtil.formatMonthDayYear(PROFILE_STATE.profile?.created ?? 0) || '—', icon: 'pi pi-calendar' },
  { label: 'User ID', value: PROFILE_STATE.profile?.id ?? '—', icon: 'pi pi-hashtag', copyable: true }
])

onMounted(() => {
  consumeConnectResult()
  load()
  void loadSignIn()
})

/** The connect round-trip returns here with ?connected=<provider> or ?error=<code>. */
function consumeConnectResult() {
  const connected = route.query.connected as string | undefined
  const error = route.query.error as string | undefined
  if (connected) {
    toast.add({ severity: 'success', summary: 'Sign-in method connected', detail: `You can now sign in with ${connected}.`, life: 6000 })
  } else if (error === 'identity_in_use') {
    toast.add({ severity: 'error', summary: 'Already connected elsewhere', detail: 'That account already signs in another Kinotic account.', life: 10000 })
  } else if (error) {
    toast.add({ severity: 'error', summary: 'Could not connect', detail: 'The provider did not complete the sign-in. Please try again.', life: 10000 })
  }
  if (connected || error) {
    const newQuery = { ...route.query }
    delete newQuery.connected
    delete newQuery.error
    router.replace({ query: newQuery })
  }
}

async function loadSignIn() {
  try {
    const [found, connectable, orgs] = await Promise.all([
      Kinotic.profile.findMySignInMethods(), Kinotic.profile.findConnectableProviders(), Kinotic.profile.findMyOrganizations()
    ])
    methods.value = found
    connectableProviders.value = connectable
    organizations.value = orgs
  } catch (err) {
    showErrorToast(toast, 'Failed to load your sign-in methods', err, { life: 8000 })
  }
}

function openPasswordDialog() {
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordDialogVisible.value = true
}

async function savePassword() {
  if (!canSavePassword.value) return
  savingPassword.value = true
  try {
    await Kinotic.profile.setPassword(hasPassword.value ? currentPassword.value : null, newPassword.value)
    passwordDialogVisible.value = false
    toast.add({ severity: 'success', summary: hasPassword.value ? 'Password changed' : 'Password set', life: 5000 })
    methods.value = await Kinotic.profile.findMySignInMethods()
  } catch (err) {
    showErrorToast(toast, 'Failed to save the password', err, { life: 8000 })
  } finally {
    savingPassword.value = false
  }
}

async function removeMethod(method: SignInMethod) {
  try {
    await Kinotic.profile.removeSignInMethod(method.id)
    methods.value = await Kinotic.profile.findMySignInMethods()
    toast.add({ severity: 'success', summary: 'Sign-in method removed', detail: method.name, life: 5000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to remove the sign-in method', err, { life: 8000 })
  }
}

/** Signs the account in to another of its organizations and reloads, so the realtime connection reconnects as that member. */
async function switchTo(organizationId: string) {
  switchingTo.value = organizationId
  try {
    const res = await fetch(apiUrl('/api/auth/org/switch'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ organizationId })
    })
    if (!res.ok) {
      throw new Error(`The switch was refused (${res.status})`)
    }
    window.location.assign('/applications')
  } catch (err) {
    showErrorToast(toast, 'Failed to switch organization', err, { life: 8000 })
    switchingTo.value = null
  }
}

onBeforeUnmount(() => {
  if (copiedTimer) clearTimeout(copiedTimer)
})

async function load() {
  try {
    await PROFILE_STATE.load()
    displayName.value = savedDisplayName.value
  } catch (err) {
    showErrorToast(toast, 'Failed to load your profile', err, { life: 8000 })
  } finally {
    loading.value = false
  }
}

function reset() {
  displayName.value = savedDisplayName.value
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    await PROFILE_STATE.updateDisplayName(displayName.value.trim())
    displayName.value = savedDisplayName.value
    toast.add({ severity: 'success', summary: 'Profile saved', life: 5000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to save your profile', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}

async function copy(label: string, value: string) {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = label
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => { copied.value = null }, COPIED_FEEDBACK_MILLIS)
  } catch (err) {
    showErrorToast(toast, `Failed to copy the ${label.toLowerCase()}`, err)
  }
}
</script>
