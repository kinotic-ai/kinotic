<template>
  <AuthPageShell>
    <div class="login-form">
      <!-- One button per enabled platform provider, so invitees can sign back in the
           same way they accepted their invitation. -->
      <div v-if="providers.length > 0" class="login-providers">
        <SocialAuthButton
          v-for="provider in providers"
          :key="provider"
          :provider="provider"
          :action="apiUrl('/api/auth/org/login/social/start/' + provider)"
          intent="sign-in"
        />
        <div class="login-divider"><span>or</span></div>
      </div>

      <!-- Single-step email + password; POST /api/auth/org/login answers a generic 401,
           so this form exposes no account-existence oracle. -->
      <div class="login-form__step">
        <IconField class="login-field">
          <InputText
            ref="emailInput"
            v-model="email"
            class="login-input"
            placeholder="Email"
            type="email"
            @keyup.enter="focusPasswordInput"
          />
        </IconField>

        <IconField class="login-field">
          <Password
            ref="passwordInput"
            v-model="password"
            input-class="login-password-input"
            class="login-password"
            placeholder="Password"
            toggleMask
            :feedback="false"
            @keyup.enter="handleSubmit"
          />
        </IconField>

        <Button
          label="Sign in"
          class="login-submit"
          :loading="loading"
          @click="handleSubmit"
        />
      </div>

      <div class="login-signup-link">
        <span>New to Kinotic?</span>
        <router-link to="/signup">Create an organization</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { CONTINUUM_UI } from '@/IContinuumUI'
import { KinoticStates } from '@/states'
import { type IUserState } from '@/states/IUserState'
import { createDebug } from '@kinotic-ai/frontend-common'
import { apiUrl, postCredentials } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'

const debug = createDebug('login')

const email = ref<string>('')
const password = ref<string>('')
const loading = ref<boolean>(false)
const providers = ref<string[]>([])

const emailInput = ref<any>()
const passwordInput = ref<any>()

const toast = useToast()
const userState: IUserState = KinoticStates.getUserState()

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  consumeUrlError()
  await loadProviders()
  nextTick(() => focusEmailInput())
})

/** Every auth flow (login, signup, invite) redirects here with ?error=<code> on failure. */
function consumeUrlError() {
  const error = (route.query.error as string | undefined)
      ?? new URLSearchParams(window.location.search).get('error')
  if (!error) return

  displayError(errorCodeToMessage(error))

  // Clean the query string so a refresh doesn't replay the error.
  const newQuery = { ...route.query }
  delete newQuery.error
  router.replace({ query: newQuery })
}

function errorCodeToMessage(code: string): string {
  switch (code) {
    case 'no_account':         return 'No account is linked to that identity. Please sign up first.'
    case 'account_exists':     return 'An account already exists for this identity. Please sign in instead.'
    case 'email_not_verified': return 'Your identity provider has not verified your email address.'
    case 'state_mismatch':     return 'Login session expired or was tampered with. Please try again.'
    case 'access_denied':      return 'You declined to authorize Kinotic. Please try again to continue.'
    case 'signup_failed':      return 'Sign-up failed. Please try again.'
    case 'exchange_failed':
    case 'provisioning_failed':
    case 'lookup_failed':
    case 'invalid_callback':
    case 'invalid_token':      return 'Sign-in failed. Please try again.'
    default:                   return `Sign-in failed: ${code}`
  }
}

async function loadProviders() {
  try {
    const res = await fetch(apiUrl('/api/auth/org/login/providers'), { credentials: 'same-origin' })
    if (!res.ok) {
      debug('Provider list returned %d', res.status)
      return
    }
    const data = await res.json()
    if (Array.isArray(data)) providers.value = data
  } catch (err) {
    debug('Failed to load providers: %O', err)
  }
}

async function handleSubmit() {
  if (!email.value) {
    displayError('Please enter your email address')
    return
  }
  if (!password.value) {
    displayError('Please enter your password')
    return
  }
  loading.value = true
  try {
    await postCredentials('/api/auth/org/login', email.value, password.value)
    // Open the realtime connection, authenticated by the freshly set session cookie.
    await userState.login()
    const referer = (route.query.referer as string | undefined) || '/applications'
    await CONTINUUM_UI.navigate(referer)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Invalid credentials'
    displayError(message)
    password.value = ''
  } finally {
    loading.value = false
  }
}

function focusEmailInput() {
  const input = emailInput.value
  input?.$el?.focus?.() ?? input?.focus?.()
}

function focusPasswordInput() {
  const input = passwordInput.value
  const inner = input?.$el?.querySelector('input[type="password"]') ?? input?.$el?.querySelector('input')
  inner?.focus?.()
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>
