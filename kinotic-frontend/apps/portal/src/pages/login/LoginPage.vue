<template>
  <AuthPageShell>
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

      <!-- The organizations a social sign-in found the identity signs in to; the person picks one. -->
      <div v-if="organizationChoices.length > 0" class="login-choices">
        <p class="login-choices__hint">You belong to several organizations. Choose one to sign in to.</p>
        <Button
          v-for="choice in organizationChoices"
          :key="choice"
          :label="choice"
          class="login-submit login-choices__choice"
          severity="secondary"
          @click="continueTo(choice)"
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
        <!-- The organizations a server that shows links in the UI answered with, in place of an email -->
        <div v-if="foundOrganizations !== null" class="login-find__found">
          <p v-if="foundOrganizations.length === 0" class="login-find__hint">This address isn't a member of any organization.</p>
          <template v-else>
            <p class="login-find__hint">This address can sign in to:</p>
            <a v-for="found in foundOrganizations" :key="found.loginUrl" :href="found.loginUrl" class="login-link">{{ found.name }}</a>
          </template>
        </div>
        <a v-if="!finding" href="#" class="login-link" @click.prevent="finding = true; foundOrganizations = null">Don't know your organization?</a>
        <template v-else>
          <p class="login-find__hint">
            {{ showLinksInUI ? 'We\'ll list the organizations this address can sign in to.' : 'We\'ll email you the organizations this address can sign in to.' }}
          </p>
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
          <!-- The Cloudflare Turnstile challenge the server requires with the request, when it names a site -->
          <div v-if="turnstileSiteKey" ref="turnstileEl" class="login-find__challenge"></div>
          <Button
            :label="showLinksInUI ? 'Find my organizations' : 'Email me my organizations'"
            class="login-submit"
            severity="secondary"
            :loading="sending"
            :disabled="turnstileSiteKey !== null && !turnstileToken"
            @click="findOrganizations"
          />
        </template>
      </div>

      <div class="login-signup-link">
        <span>New to Kinotic?</span>
        <router-link to="/signup">Create Organization</router-link>
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
import { organizationLoginPath, organizationSlug, rememberedOrganization, UNKNOWN_ORGANIZATION_STATE } from '@/pages/login/organizationLogin'
import { emailNotVerifiedMessage } from '@/util/oidcConfigurations'

const debug = createDebug('login')

/** Providers offered on this page; any other enabled provider is omitted. */
const LOGIN_PROVIDERS = ['github']

const organization = ref<string>('')
const email = ref<string>('')
const finding = ref<boolean>(false)
const sending = ref<boolean>(false)
const turnstileSiteKey = ref<string | null>(null)
// whether the find route answers with the organizations in place of an email
const showLinksInUI = ref<boolean>(false)
const foundOrganizations = ref<{ name: string, loginUrl: string }[] | null>(null)
const turnstileToken = ref<string>('')
const turnstileEl = ref<HTMLElement | null>(null)
let turnstileWidget: string | null = null

/** The Cloudflare Turnstile API its challenge script defines on the page. */
interface Turnstile {
  render(container: HTMLElement, options: {
    sitekey: string
    callback: (token: string) => void
    'expired-callback'?: () => void
    'error-callback'?: () => void
  }): string
  reset(widgetId: string): void
  remove(widgetId: string): void
}

declare global {
  interface Window { turnstile?: Turnstile }
}

const TURNSTILE_SCRIPT = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let turnstileLoading: Promise<Turnstile> | null = null
const providers = ref<string[]>([])
const organizationChoices = ref<string[]>([])

const organizationInput = ref<any>()
const emailInput = ref<any>()

const toast = useToast()

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const remembered = rememberedOrganization()
  // a returning browser goes straight to the organization it last signed in to, unless a failed
  // sign-in or a choice of organizations must be shown here
  if (remembered !== null && urlParam('error') === null) {
    router.replace({ path: organizationLoginPath(remembered), query: route.query })
  } else {
    void consumeUrlError()
    const unknown = unknownOrganization()
    if (unknown !== null) {
      displayError(`There is no organization named ${unknown}.`)
    }
    organization.value = remembered ?? unknown ?? ''
    nextTick(() => focus(organizationInput))
    // the buttons appear when the list arrives, so a server slow to answer does not hold up the form
    void loadProviders()
  }
})

watch(finding, open => {
  if (open) {
    nextTick(() => focus(emailInput))
    void presentChallenge()
  } else {
    dismissChallenge()
  }
})

// The find route is open to anyone, so the server may require a Turnstile token with the request: GET names
// the site whose challenge the page presents, or nothing when the deployment requires none
async function presentChallenge() {
  try {
    const res = await fetch(apiUrl('/api/auth/org/login/find'), { credentials: 'same-origin' })
    const data = res.ok ? await res.json() : {}
    turnstileSiteKey.value = typeof data?.turnstileSiteKey === 'string' ? data.turnstileSiteKey : null
    showLinksInUI.value = data?.showLinksInUI === true
  } catch (err) {
    debug('Failed to load the find challenge: %O', err)
    turnstileSiteKey.value = null
  }
  if (!turnstileSiteKey.value) return
  try {
    const turnstile = await loadTurnstile()
    await nextTick()
    if (turnstileEl.value && finding.value) {
      turnstileWidget = turnstile.render(turnstileEl.value, {
        sitekey: turnstileSiteKey.value,
        callback: token => { turnstileToken.value = token },
        'expired-callback': () => { turnstileToken.value = '' },
        'error-callback': () => { turnstileToken.value = '' },
      })
    }
  } catch (err) {
    debug('Failed to present the challenge: %O', err)
    displayError('Could not load the verification. Please try again later.')
  }
}

function dismissChallenge() {
  if (turnstileWidget) {
    window.turnstile?.remove(turnstileWidget)
    turnstileWidget = null
  }
  turnstileToken.value = ''
  turnstileSiteKey.value = null
}

// Loaded once, the first time a challenge is presented; the script defines window.turnstile
function loadTurnstile(): Promise<Turnstile> {
  if (!turnstileLoading) {
    turnstileLoading = new Promise((resolve, reject) => {
      if (window.turnstile) {
        resolve(window.turnstile)
        return
      }
      const script = document.createElement('script')
      script.src = TURNSTILE_SCRIPT
      script.async = true
      script.onload = () => window.turnstile ? resolve(window.turnstile) : reject(new Error('Turnstile did not load'))
      script.onerror = () => reject(new Error('Turnstile did not load'))
      document.head.appendChild(script)
    })
  }
  return turnstileLoading
}

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
 * that found the identity signs in to several organizations left their ids in the session, and the page
 * offers them instead of an error.
 */
async function consumeUrlError() {
  const error = urlParam('error')
  if (!error) return
  const provider = urlParam('provider')

  // Clean the query string so a refresh doesn't replay the error; the referer stays, for the login
  // the person starts next from here.
  const newQuery = { ...route.query }
  delete newQuery.error
  delete newQuery.provider
  router.replace({ query: newQuery })

  if (error !== 'multiple_organizations') {
    displayError(errorCodeToMessage(error, provider))
  } else {
    const choices = await loadChoices()
    if (choices === null) {
      displayError('Could not load your organizations. Please sign in again.')
    } else if (choices.length === 0) {
      displayError(errorCodeToMessage(error, provider))
    } else {
      organizationChoices.value = choices
    }
  }
}

/**
 * The remembered organization the organization page sent the browser back from because it no longer
 * exists, or null when the page was reached any other way.
 */
function unknownOrganization(): string | null {
  const name = window.history.state?.[UNKNOWN_ORGANIZATION_STATE]
  return typeof name === 'string' && name ? name : null
}

// The organizations the sign-in found, offered for a short while after it; null when the request
// itself failed, where an empty list is the server's answer.
async function loadChoices(): Promise<string[] | null> {
  let ret: string[] | null = null
  try {
    const res = await fetch(apiUrl('/api/auth/org/login/choices'), { credentials: 'include' })
    if (res.ok) {
      const data = await res.json()
      ret = Array.isArray(data) ? data.filter((id): id is string => typeof id === 'string' && id.length > 0) : []
    } else {
      debug('Organization choices returned %d', res.status)
    }
  } catch (err) {
    debug('Failed to load the organization choices: %O', err)
  }
  return ret
}

function urlParam(name: string): string | null {
  return (route.query[name] as string | undefined) ?? new URLSearchParams(window.location.search).get(name)
}

function errorCodeToMessage(code: string, provider: string | null): string {
  switch (code) {
    case 'no_user':                return 'That identity signs in to no Kinotic organization. Please sign up first.'
    case 'multiple_organizations': return 'You belong to several organizations, but the choice has expired. Please sign in again.'
    case 'user_disabled':          return 'You cannot sign in to this organization. Ask an administrator to enable your access.'
    case 'email_not_verified':     return emailNotVerifiedMessage(provider)
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
      body: JSON.stringify({ email: email.value, turnstileToken: turnstileToken.value || undefined })
    })
    if (!res.ok) {
      throw new Error(await readAuthError(res, 'Could not send the email'))
    }
    if (res.status === 200) {
      foundOrganizations.value = (await res.json()).organizations ?? []
    } else {
      toast.add({ severity: 'success', summary: 'Email sent', detail: `Check ${email.value} for your organizations.`, life: 10000 })
    }
    finding.value = false
  } catch (err) {
    displayError(err instanceof Error ? err.message : 'Could not send the email')
  } finally {
    sending.value = false
    // a token verifies once, so the next attempt needs a new one
    if (turnstileWidget) {
      window.turnstile?.reset(turnstileWidget)
      turnstileToken.value = ''
    }
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
.login-providers :deep(.social-auth-button--mark) {
  width: 100%;
}

.login-providers :deep(.social-auth-button--mark) {
  height: 3.5rem;
  border-radius: 5px;
  border-color: var(--lp-provider-border);
  font-weight: 400;
}

.login-providers :deep(.social-auth-button--mark:hover) {
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

.login-choices {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 2.25rem;
}

.login-choices__hint {
  margin: 0 0 0.5rem;
  text-align: center;
  color: var(--lp-text);
}

.login-choices .login-divider {
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

.login-find__found {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.login-find__challenge {
  display: flex;
  justify-content: center;
  min-height: 65px;
  margin: 0 0 1rem;
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
