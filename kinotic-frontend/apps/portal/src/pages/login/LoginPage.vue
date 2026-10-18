<template>
  <AuthPageShell :art="loginArt">
    <div class="login-form">
      <div v-if="providers.length > 0" class="login-providers">
        <SocialAuthButton
          v-for="provider in providers"
          :key="provider"
          :provider="provider"
          :action="socialStartUrl(provider)"
          intent="sign-in"
        />
        <div class="login-divider"><span>or</span></div>
      </div>

      <!-- The organizations a social sign-in found the identity a member of; the person picks one. -->
      <div v-if="memberships.length > 0" class="login-memberships">
        <p class="login-memberships__hint">Your account belongs to several organizations. Choose one to sign in to.</p>
        <Button
          v-for="membership in memberships"
          :key="membership"
          :label="membership"
          class="login-submit login-memberships__choice"
          severity="secondary"
          @click="continueTo(membership)"
        />
        <div class="login-divider"><span>or</span></div>
      </div>

      <!-- A login is addressed to an organization: its page, /login/<organization>, offers the
           methods that organization's login supports. -->
      <div class="login-form__step">
        <IconField class="login-field login-field--last">
          <InputText
            ref="organizationInput"
            v-model="organization"
            class="login-input"
            placeholder="Organization"
            autocapitalize="none"
            autocorrect="off"
            spellcheck="false"
            @keyup.enter="continueToOrganization"
          />
        </IconField>

        <Button
          label="Continue"
          class="login-submit"
          @click="continueToOrganization"
        />
      </div>

      <div class="login-find">
        <a v-if="!finding" href="#" class="login-link" @click.prevent="finding = true">Don't know your organization?</a>
        <template v-else>
          <p class="login-find__hint">We'll email you the organizations this address can sign in to.</p>
          <IconField class="login-field login-field--last">
            <InputText
              ref="emailInput"
              v-model="email"
              class="login-input"
              placeholder="Email"
              type="email"
              @keyup.enter="findOrganizations"
            />
          </IconField>
          <Button
            label="Email me my organizations"
            class="login-submit"
            severity="secondary"
            :loading="sending"
            @click="findOrganizations"
          />
        </template>
      </div>

      <div class="login-signup-link">
        <span>New to Kinotic?</span>
        <router-link to="/signup">Create Account</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { createDebug } from '@kinotic-ai/frontend-common'
import { apiUrl, readAuthError } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
import loginArt from '@/assets/login-background.svg'
import { LAST_ORGANIZATION_KEY, organizationLoginPath, organizationSlug } from '@/pages/login/organizationLogin'

const debug = createDebug('login')

/** Providers offered on this page; any other enabled provider is omitted. */
const LOGIN_PROVIDERS = ['github']

const organization = ref<string>('')
const email = ref<string>('')
const finding = ref<boolean>(false)
const sending = ref<boolean>(false)
const providers = ref<string[]>([])
const memberships = ref<string[]>([])

const organizationInput = ref<any>()
const emailInput = ref<any>()

const toast = useToast()

const route = useRoute()
const router = useRouter()

onMounted(() => {
  consumeUrlError()
  // the organization the browser last signed in to, so a returning member only confirms it
  organization.value = localStorage.getItem(LAST_ORGANIZATION_KEY) ?? ''
  nextTick(() => focus(organizationInput))
  // the buttons appear when the list arrives, so a server slow to answer does not hold up the form
  void loadProviders()
})

watch(finding, open => {
  if (open) nextTick(() => focus(emailInput))
})

/**
 * The gateway endpoint that starts a social login, carrying the router guard's `referer` so the
 * gateway can send the browser back there. The password path navigates client side, but a social
 * login leaves the SPA entirely and returns through a gateway redirect.
 */
function socialStartUrl(provider: string): string {
  const url = apiUrl('/api/auth/org/login/social/start/' + provider)
  const referer = route.query.referer as string | undefined
  return referer ? `${url}?referer=${encodeURIComponent(referer)}` : url
}

/**
 * Every auth flow (login, signup, invite) redirects here with ?error=<code> on failure. A social sign-in
 * that found the identity a member of several organizations names them in ?organizations=, and the page
 * offers them instead of an error.
 */
function consumeUrlError() {
  const search = new URLSearchParams(window.location.search)
  const error = (route.query.error as string | undefined) ?? search.get('error')
  if (!error) return

  const organizations = (route.query.organizations as string | undefined) ?? search.get('organizations')
  if (error === 'multiple_organizations' && organizations) {
    memberships.value = organizations.split(',').filter(id => id.length > 0)
  } else {
    displayError(errorCodeToMessage(error))
  }

  // Clean the query string so a refresh doesn't replay the error.
  const newQuery = { ...route.query }
  delete newQuery.error
  delete newQuery.organizations
  router.replace({ query: newQuery })
}

function errorCodeToMessage(code: string): string {
  switch (code) {
    case 'no_account':             return 'No account is linked to that identity. Please sign up first.'
    case 'account_exists':         return 'An account already exists for this email. Sign in the way you usually do, then connect this provider under Sign-in methods on your profile.'
    case 'email_not_verified':     return 'Your identity provider has not verified your email address.'
    case 'state_mismatch':         return 'Login session expired or was tampered with. Please try again.'
    case 'access_denied':          return 'You declined to authorize Kinotic. Please try again to continue.'
    case 'signup_failed':          return 'Sign-up failed. Please try again.'
    case 'exchange_failed':
    case 'provisioning_failed':
    case 'lookup_failed':
    case 'invalid_callback':
    case 'invalid_token':          return 'Sign-in failed. Please try again.'
    default:                       return `Sign-in failed: ${code}`
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
    if (Array.isArray(data)) providers.value = data.filter(p => LOGIN_PROVIDERS.includes(p))
  } catch (err) {
    debug('Failed to load providers: %O', err)
  }
}

function continueToOrganization() {
  const slug = organizationSlug(organization.value)
  if (!slug) {
    displayError('Please enter your organization')
    return
  }
  continueTo(slug)
}

function continueTo(organizationId: string) {
  router.push({ path: organizationLoginPath(organizationId), query: route.query })
}

async function findOrganizations() {
  if (!email.value) {
    displayError('Please enter your email address')
    return
  }
  sending.value = true
  try {
    const res = await fetch(apiUrl('/api/auth/org/login/find'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value })
    })
    if (!res.ok) {
      throw new Error(await readAuthError(res, 'Could not send the email'))
    }
    toast.add({ severity: 'success', summary: 'Email sent', detail: `Check ${email.value} for your organizations.`, life: 10000 })
    finding.value = false
  } catch (err) {
    displayError(err instanceof Error ? err.message : 'Could not send the email')
  } finally {
    sending.value = false
  }
}

function focus(input: { value?: any }) {
  const el = input.value
  el?.$el?.focus?.() ?? el?.focus?.()
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>

<style scoped>
.login-form {
  margin-top: 1rem;
}

.login-providers {
  margin: 0 0 3.25rem;
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
  margin: 2.25rem 0 0;
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

.login-memberships {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 2.25rem;
}

.login-memberships__hint {
  margin: 0 0 0.5rem;
  text-align: center;
  color: var(--lp-text);
}

.login-memberships .login-divider {
  margin-top: 1.5rem;
}

.login-find {
  margin-top: 1.5rem;
  font-size: 0.9375rem;
  text-align: center;
}

.login-find__hint {
  margin: 0 0 1rem;
  color: var(--lp-text);
}

.login-signup-link {
  flex-direction: row;
  justify-content: center;
  gap: 0.375rem;
  margin-top: 1.5rem;
  font-size: 1rem;
  color: var(--lp-text);
}
</style>
