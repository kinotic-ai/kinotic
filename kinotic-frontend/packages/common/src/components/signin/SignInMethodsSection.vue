<template>
  <div>
      <!-- Several ways in: the password and every provider identity bound to the user. The last
           remaining method cannot be removed, so the user always keeps a way to sign in. -->
      <section class="rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-1 px-6 pt-6">
          <h3 class="text-sm font-medium">Sign-in methods</h3>
          <small class="text-muted-color">{{ description }}</small>
        </div>

        <ul class="mt-4 border-t border-surface-200 dark:border-surface-700">
          <li v-for="method in methods" :key="method.id"
              class="flex flex-wrap items-center gap-3 border-b border-surface-100 px-6 py-3.5 dark:border-surface-800">
            <component :is="method.type === AuthType.PASSWORD ? KeyRound : ShieldCheck" :size="18" :stroke-width="1.75"
                       class="shrink-0 text-surface-600 dark:text-surface-300" aria-hidden="true" />
            <div class="flex min-w-0 flex-1 flex-col">
              <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ method.name }}</span>
              <span v-if="method.created" class="text-xs text-muted-color">Connected {{ DatetimeUtil.formatMonthDayYear(method.created) }}</span>
            </div>
            <Button v-if="method.type === AuthType.PASSWORD" label="Change password" size="small" severity="secondary" outlined
                    @click="openPasswordDialog" />
            <Button v-else label="Remove" size="small" severity="secondary" text :disabled="methods.length < 2"
                    v-tooltip.top="methods.length < 2 ? 'Add another sign-in method first' : undefined"
                    @click="removeMethod(method)" />
          </li>
          <li v-if="!hasPassword && passwordOffered" class="flex flex-wrap items-center gap-3 border-b border-surface-100 px-6 py-3.5 dark:border-surface-800">
            <KeyRound :size="18" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
            <span class="flex-1 text-sm text-muted-color">No password set</span>
            <Button label="Set password" size="small" severity="secondary" outlined @click="openPasswordDialog" />
          </li>
        </ul>

        <div v-if="connectableProviders.length > 0" class="flex flex-col gap-3 px-6 py-4">
          <span class="text-xs text-muted-color">Connect another way to sign in:</span>
          <div class="grid grid-cols-[repeat(auto-fill,minmax(15rem,1fr))] gap-3">
            <SocialAuthButton v-for="provider in connectableProviders" :key="provider.configurationId" :provider="provider.provider"
                              :name="provider.type === OidcConfigurationType.ORG_SIGNUP ? undefined : provider.name"
                              :action="apiUrl(connectPath + encodeURIComponent(provider.configurationId))" intent="connect" />
          </div>
        </div>
      </section>

    <FormDrawer :visible="passwordFormVisible"
                :title="hasPassword ? 'Change password' : 'Set a password'"
                :description="hasPassword ? 'Enter your current password, then the new one.' : 'A password lets you sign in without a provider.'"
                @close="passwordFormVisible = false">
      <form id="password-form" @submit.prevent="savePassword">
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
      </form>
      <template #footer>
        <Button type="button" label="Cancel" severity="secondary" outlined @click="passwordFormVisible = false" />
        <Button type="submit" form="password-form" :label="hasPassword ? 'Change password' : 'Set password'" :loading="savingPassword" :disabled="!canSavePassword" />
      </template>
    </FormDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Password from 'primevue/password'
import { useToast } from 'primevue/usetoast'
import { KeyRound, ShieldCheck } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type LoginProviderSummary, OidcConfigurationType, PASSWORD_SIGN_IN_METHOD_ID, type SignInMethod, AuthType } from '@kinotic-ai/management-api'
import FormDrawer from '../FormDrawer.vue'
import SocialAuthButton from '../SocialAuthButton.vue'
import DatetimeUtil from '../../util/DatetimeUtil'
import { apiUrl, showErrorToast } from '../../util/helpers'
import { emailNotVerifiedMessage } from '../../util/oidcProviders'

/**
 * The signed-in user's own sign-in methods at its login, an organization's or the system console's: the password
 * and every provider identity bound to the user, changed, removed and connected from here. The last method that
 * still signs the user in cannot be removed. A connect round-trip returns to the page showing this with
 * `?connected=<provider>` or `?error=<code>`.
 */
const props = defineProps<{
  /** What the login is, as in "How you sign in to this organization." */
  description: string
  /** The route a provider is connected through, which the configuration's id is appended to. */
  connectPath: string
  /** The login's page of providers, which says whether it takes an email and a password. */
  providersPath: string
}>()

const toast = useToast()
const route = useRoute()
const router = useRouter()

const methods = ref<SignInMethod[]>([])
const connectableProviders = ref<LoginProviderSummary[]>([])
/** Whether the login takes an email and a password, so the user may set one. */
const passwordOffered = ref(false)
const passwordFormVisible = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const savingPassword = ref(false)

const hasPassword = computed(() => methods.value.some(method => method.id === PASSWORD_SIGN_IN_METHOD_ID))
const canSavePassword = computed(() => newPassword.value.length > 0 && newPassword.value === confirmPassword.value
    && (!hasPassword.value || currentPassword.value.length > 0))

onMounted(() => {
  consumeConnectResult()
  void loadSignIn()
})

/** The connect round-trip returns here with ?connected=<provider> or ?error=<code>, naming a provider key in ?provider. */
function consumeConnectResult() {
  const connected = route.query.connected as string | undefined
  const error = route.query.error as string | undefined
  if (connected) {
    toast.add({ severity: 'success', summary: 'Sign-in method connected', detail: `You can now sign in with ${connected}.`, life: 6000 })
  } else if (error === 'identity_in_use') {
    toast.add({ severity: 'error', summary: 'Already connected elsewhere', detail: 'That identity already signs in another Kinotic user.', life: 10000 })
  } else if (error === 'email_not_verified') {
    toast.add({ severity: 'error', summary: 'Could not connect', detail: emailNotVerifiedMessage(route.query.provider as string | undefined), life: 10000 })
  } else if (error) {
    toast.add({ severity: 'error', summary: 'Could not connect', detail: 'The provider did not complete the sign-in. Please try again.', life: 10000 })
  }
  if (connected || error) {
    const newQuery = { ...route.query }
    delete newQuery.connected
    delete newQuery.error
    delete newQuery.provider
    router.replace({ query: newQuery })
  }
}

async function loadSignIn() {
  try {
    const [found, connectable, offered] = await Promise.all([
      Kinotic.profile.findMySignInMethods(), Kinotic.profile.findConnectableProviders(), findPasswordOffered()
    ])
    methods.value = found
    connectableProviders.value = connectable
    passwordOffered.value = offered
  } catch (err) {
    showErrorToast(toast, 'Failed to load your sign-in methods', err, { life: 8000 })
  }
}

async function findPasswordOffered(): Promise<boolean> {
  const res = await fetch(apiUrl(props.providersPath), { credentials: 'same-origin' })
  const providers: { type: string }[] = res.ok ? await res.json() : []
  return providers.some(provider => provider.type === 'PASSWORD')
}

function openPasswordDialog() {
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordFormVisible.value = true
}

async function savePassword() {
  if (!canSavePassword.value) return
  savingPassword.value = true
  try {
    await Kinotic.profile.setPassword(hasPassword.value ? currentPassword.value : null, newPassword.value)
    passwordFormVisible.value = false
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
</script>
