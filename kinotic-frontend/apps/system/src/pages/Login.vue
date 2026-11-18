<template>
  <AuthPageShell>
    <div class="login-form">
      <div v-if="oidcProviders.length > 0" class="login-providers">
        <SocialAuthButton
          v-for="provider in oidcProviders"
          :key="provider.id"
          :provider="provider.provider!"
          :name="provider.name"
          :action="startUrl(provider.id)"
          intent="sign-in"
        />
        <div v-if="passwordSignIn" class="login-divider"><span>or</span></div>
      </div>

      <div v-if="passwordSignIn" class="login-form__step">
        <IconField class="login-field">
          <InputText
            ref="emailInput"
            v-model="email"
            class="login-input"
            placeholder="Email"
            @keyup.enter="handleSubmit"
          />
        </IconField>

        <IconField class="login-field">
          <Password
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
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { apiUrl, AuthPageShell, createDebug, emailNotVerifiedMessage, postCredentials, SocialAuthButton } from '@kinotic-ai/frontend-common'

import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const debug = createDebug('system-login')

const email = ref<string>('')
const password = ref<string>('')
const loading = ref<boolean>(false)

/** A way to sign in the login offers, as `GET /api/auth/system/login/providers` lists it. */
interface SignInProvider {
  id: string
  type: 'OIDC' | 'PASSWORD'
  name: string
  provider: string | null
}

const providers = ref<SignInProvider[]>([])
// the list could not be read, so the form is offered anyway; the server still refuses a password its login does not take
const providersUnavailable = ref(false)
// the form shows only while the console's login takes a password
const passwordSignIn = computed(() => providersUnavailable.value || providers.value.some(provider => provider.type === 'PASSWORD'))
const oidcProviders = computed(() => providers.value.filter(provider => provider.type === 'OIDC'))

const emailInput = ref<any>()

const toast = useToast()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  consumeUrlError()
  void loadProviders()
})

/**
 * The gateway endpoint that starts a sign-in through a registration, carrying the router guard's `referer` so the
 * gateway can send the browser back there once signed in.
 */
function startUrl(configurationId: string): string {
  const url = apiUrl(`/api/auth/system/login/start/${encodeURIComponent(configurationId)}`)
  const referer = route.query.referer as string | undefined
  return referer ? `${url}?referer=${encodeURIComponent(referer)}` : url
}

async function loadProviders() {
  try {
    const res = await fetch(apiUrl('/api/auth/system/login/providers'), { credentials: 'same-origin' })
    if (res.ok) {
      providers.value = await res.json()
      if (passwordSignIn.value) {
        // the form renders once the list says it is offered
        await nextTick()
        const input = emailInput.value
        input?.$el?.focus?.() ?? input?.focus?.()
      }
    } else {
      debug('Provider list returned %d', res.status)
      providersUnavailable.value = true
    }
  } catch (err) {
    debug('Failed to load providers: %O', err)
    providersUnavailable.value = true
  }
}

/** A provider sign-in that failed redirects here with `?error=<code>`, and `provider` naming the provider when known. */
function consumeUrlError() {
  const error = route.query.error as string | undefined
  if (error) {
    const provider = route.query.provider as string | undefined
    // a refresh must not replay the error; the referer stays, for the sign-in started next from here
    const query = { ...route.query }
    delete query.error
    delete query.provider
    void router.replace({ query })
    displayError(errorCodeToMessage(error, provider))
  }
}

function errorCodeToMessage(code: string, provider: string | undefined): string {
  switch (code) {
    case 'no_user':             return 'No system user is added under that account. Ask a system user to add you under Members.'
    case 'user_disabled':       return 'Your access to the system console is disabled.'
    case 'email_not_verified':  return emailNotVerifiedMessage(provider)
    case 'state_mismatch':      return 'The sign-in expired. Please try again.'
    case 'access_denied':       return 'You declined to authorize Kinotic. Please try again to continue.'
    case 'config_not_found':    return 'That sign-in is not available.'
    default:                    return 'Sign-in failed. Please try again.'
  }
}

async function handleSubmit() {
  if (!email.value || !password.value) {
    displayError('Please enter your email and password')
    return
  }
  loading.value = true
  try {
    await postCredentials('/api/auth/system/login', email.value, password.value)
    // Open the realtime connection, authenticated by the freshly set session cookie.
    await SYSTEM_USER_STATE.login()
    const referer = (route.query.referer as string | undefined) || '/dashboard'
    await router.push(referer)
  } catch (err) {
    debug('Login failed: %O', err)
    displayError(err instanceof Error ? err.message : 'Invalid credentials')
    password.value = ''
  } finally {
    loading.value = false
  }
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>

<style scoped>
.login-providers {
  margin: 0 0 3.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.login-divider {
  margin: 1.5rem 0 0;
  font-size: 0.75rem;
  text-transform: uppercase;
}
</style>
