<template>
  <AuthPageShell :art="loginArt">
    <div class="login-form">
      <div class="login-organization">
        <span class="login-organization__label">Signing in to</span>
        <span class="login-organization__name">{{ organizationId }}</span>
        <router-link :to="{ path: '/login', query: route.query }" class="login-link login-organization__switch">
          Not your organization?
        </router-link>
      </div>

      <template v-if="unknown">
        <p class="login-unknown">There is no organization named <strong>{{ organizationId }}</strong>.</p>
      </template>
      <template v-else>
        <div v-if="providers.length > 0" class="login-providers">
          <SocialAuthButton
            v-for="provider in providers"
            :key="provider.id"
            :provider="provider.provider"
            :action="startUrl(provider.id)"
            intent="sign-in"
          />
          <div class="login-divider"><span>or</span></div>
        </div>

        <!-- Single-step email + password; the login answers a generic 401, so this form
             exposes no account-existence oracle. -->
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

          <IconField class="login-field login-field--last">
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
      </template>

      <div class="login-signup-link">
        <span>New to Kinotic?</span>
        <router-link to="/signup">Create Account</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { KINOTIC_UI } from '@/IKinoticUI'
import { KinoticStates } from '@/states'
import { type IUserState } from '@/states/IUserState'
import { createDebug } from '@kinotic-ai/frontend-common'
import { apiUrl, postCredentials } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
import loginArt from '@/assets/login-background.svg'
import { LAST_ORGANIZATION_KEY } from '@/pages/login/organizationLogin'

const debug = createDebug('login')

/** A configuration the organization's login offers, as `GET /api/auth/org/login/:orgId/providers` lists it. */
interface LoginProvider {
  id: string
  name: string
  provider: string
}

const props = defineProps<{ organizationId: string }>()

const email = ref<string>('')
const password = ref<string>('')
const loading = ref<boolean>(false)
const unknown = ref<boolean>(false)
const providers = ref<LoginProvider[]>([])

const emailInput = ref<any>()
const passwordInput = ref<any>()

const toast = useToast()
const userState: IUserState = KinoticStates.getUserState()

const route = useRoute()

onMounted(() => {
  nextTick(() => focusEmailInput())
  // the buttons appear when the list arrives, so a server slow to answer does not hold up the form
  void loadProviders()
})

/**
 * The gateway endpoint that starts a sign-in through one of the organization's configurations,
 * carrying the router guard's `referer` so the gateway can send the browser back there. The
 * password path navigates client side, but a provider sign-in leaves the SPA entirely and returns
 * through a gateway redirect.
 */
function startUrl(configurationId: string): string {
  const url = apiUrl(`/api/auth/org/login/${encodeURIComponent(props.organizationId)}/start/${encodeURIComponent(configurationId)}`)
  const referer = route.query.referer as string | undefined
  return referer ? `${url}?referer=${encodeURIComponent(referer)}` : url
}

async function loadProviders() {
  try {
    const res = await fetch(apiUrl(`/api/auth/org/login/${encodeURIComponent(props.organizationId)}/providers`),
                            { credentials: 'same-origin' })
    if (res.status === 404) {
      unknown.value = true
      return
    }
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
    await postCredentials(`/api/auth/org/login/${encodeURIComponent(props.organizationId)}`, email.value, password.value)
    localStorage.setItem(LAST_ORGANIZATION_KEY, props.organizationId)
    // Open the realtime connection, authenticated by the freshly set session cookie.
    await userState.login()
    const referer = (route.query.referer as string | undefined) || '/applications'
    await KINOTIC_UI.navigate(referer)
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

<style scoped>
.login-form {
  margin-top: 1rem;
}

.login-organization {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  margin-bottom: 2rem;
  color: var(--lp-text);
}

.login-organization__label {
  font-size: 0.8125rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.login-organization__name {
  font-size: 1.25rem;
  font-weight: 600;
}

.login-organization__switch {
  font-size: 0.875rem;
}

.login-unknown {
  margin: 0 0 1.5rem;
  text-align: center;
  color: var(--lp-text);
}

.login-providers {
  margin: 0 0 3.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.login-providers :deep(.social-auth-button-form),
.login-providers :deep(.social-auth-button--gh) {
  width: 100%;
}

.login-providers :deep(.social-auth-button--gh) {
  height: 3.5rem;
  border-radius: 5px;
  border-color: var(--lp-provider-border);
  font-weight: 400;
}

.login-providers :deep(.social-auth-button--gh:hover) {
  border-color: var(--lp-provider-border-hover);
}

.login-divider {
  margin: 1.5rem 0 0;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.login-field--last {
  margin-bottom: 1.5rem;
}

/* Chrome paints autofilled fields yellow; keep them on the input surface. */
.login-form :deep(input:-webkit-autofill) {
  box-shadow: 0 0 0 1000px var(--lp-input-bg) inset;
  -webkit-text-fill-color: var(--lp-input-color);
}

.login-signup-link {
  flex-direction: row;
  justify-content: center;
  gap: 0.375rem;
  margin-top: 1rem;
  font-size: 1rem;
  color: var(--lp-text);
}
</style>
