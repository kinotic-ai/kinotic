<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="loginBackgroundArt" alt="" class="login-art__image" />
      </aside>

      <main class="login-panel">
        <button type="button" class="login-theme-toggle" @click="toggleTheme"
                :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'">
          <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
        </button>
        <div class="login-panel__content">
          <img :src="loginBrandMark" alt="Kinotic" class="login-brand" />

          <div class="login-form">
            <!-- Platform social buttons (Google, Microsoft, etc.) — fetched from /api/login/providers -->
            <div v-if="providers.length > 0" class="login-providers">
              <form
                v-for="provider in providers"
                :key="provider"
                method="post"
                :action="apiUrl('/api/login/start/' + provider)"
                class="login-provider-form"
              >
                <button type="submit" class="login-provider__button">
                  <span class="login-provider__label">Continue with {{ providerLabel(provider) }}</span>
                </button>
              </form>
              <div class="login-divider"><span>or</span></div>
            </div>

            <!-- Email field — first step -->
            <div v-if="step === 'email'" class="login-form__step">
              <IconField class="login-field">
                <InputText
                  ref="emailInput"
                  v-model="email"
                  class="login-input"
                  placeholder="Email"
                  @keyup.enter="handleEmailSubmit"
                />
              </IconField>

              <Button
                label="Continue"
                class="login-submit"
                :loading="loading"
                @click="handleEmailSubmit"
              />
            </div>

            <!-- Password field — shown after lookup says "password" -->
            <div v-else-if="step === 'password'" class="login-password-step">
              <IconField class="login-field">
                <InputText
                  :value="email"
                  disabled
                  class="login-input login-input--disabled"
                  placeholder="Email"
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
                  @keyup.enter="handlePasswordSubmit"
                />
              </IconField>

              <Button
                label="Sign in"
                class="login-submit"
                :loading="loading"
                @click="handlePasswordSubmit"
              />

              <button type="button" class="login-back-link" @click="resetToEmail">
                <span class="pi pi-angle-left login-back-link__icon" aria-hidden="true"></span>
                <span>Back</span>
              </button>
            </div>

            <!-- SSO redirect spinner — brief moment before window.location changes -->
            <div v-else-if="step === 'redirecting'" class="login-loading-state">
              <div class="login-spinner login-spinner--small"></div>
              <p class="login-loading-state__text">Redirecting to your identity provider…</p>
            </div>

            <div class="login-signup-link">
              <span>New to Kinotic?</span>
              <router-link to="/signup">Create an organization</router-link>
            </div>
          </div>
        </div>

        <footer class="login-footer">
          <a href="#" class="login-footer__link">Terms of use</a>
          <span class="login-footer__divider">|</span>
          <a href="#" class="login-footer__link">Privacy policy</a>
        </footer>
      </main>
    </div>

    <Toast />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import IconField from 'primevue/iconfield'
import { useToast } from 'primevue/usetoast'

import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'
import { createDebug } from '@/util/debug'
import { apiUrl } from '@/util/helpers'
import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import '@/pages/auth-pages.css'

const debug = createDebug('login')

type Step = 'email' | 'password' | 'redirecting'

interface LookupResponse {
  type: 'sso' | 'password'
  redirect?: string
}

@Component({
  components: { InputText, Password, Button, Toast, IconField }
})
export default class Login extends Vue {
  email: string = ''
  password: string = ''
  step: Step = 'email'
  loading: boolean = false
  providers: string[] = []

  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  async mounted() {
    this.consumeUrlError()
    await this.loadProviders()
    this.$nextTick(() => this.focusEmailInput())
  }

  private consumeUrlError() {
    const error = (this.$route.query.error as string | undefined)
        ?? new URLSearchParams(window.location.search).get('error')
    if (!error) return

    this.displayError(this.errorCodeToMessage(error))

    // Clean the query string so a refresh doesn't replay the error.
    const newQuery = { ...this.$route.query }
    delete newQuery.error
    this.$router.replace({ query: newQuery })
  }

  private errorCodeToMessage(code: string): string {
    switch (code) {
      case 'no_account':       return 'No account is linked to that identity. Please sign up first.'
      case 'account_exists':   return 'An account already exists for this identity. Please sign in instead.'
      case 'email_not_verified': return 'Your identity provider has not verified your email address.'
      case 'state_mismatch':   return 'Login session expired or was tampered with. Please try again.'
      case 'access_denied':    return 'You declined to authorize Kinotic. Please try again to continue.'
      case 'exchange_failed':
      case 'provisioning_failed':
      case 'lookup_failed':
      case 'invalid_callback':
      case 'invalid_token':    return 'Sign-in failed. Please try again.'
      default:                 return `Sign-in failed: ${code}`
    }
  }

  private async loadProviders() {
    try {
      const res = await fetch(apiUrl('/api/login/providers'), { credentials: 'same-origin' })
      if (!res.ok) {
        debug('Provider list returned %d', res.status)
        return
      }
      const data = await res.json()
      if (Array.isArray(data)) this.providers = data
    } catch (err) {
      debug('Failed to load providers: %O', err)
    }
  }

  async handleEmailSubmit() {
    if (!this.email) {
      this.displayError('Please enter an email address')
      return
    }
    this.loading = true
    try {
      const res = await fetch(apiUrl('/api/login/lookup'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ email: this.email })
      })
      if (!res.ok) {
        const message = await this.readError(res, 'Lookup failed')
        this.displayError(message)
        return
      }
      const data = await res.json() as LookupResponse
      if (data.type === 'sso' && data.redirect) {
        this.step = 'redirecting'
        window.location.href = data.redirect
        return
      }
      this.step = 'password'
      this.$nextTick(() => this.focusPasswordInput())
    } catch (err) {
      debug('Email lookup failed: %O', err)
      this.displayError('Could not contact the server')
    } finally {
      this.loading = false
    }
  }

  async handlePasswordSubmit() {
    if (!this.password) {
      this.displayError('Please enter your password')
      return
    }
    this.loading = true
    try {
      // Trade email + password for a Kinotic JWT, then open STOMP with the same Bearer
      // path the OIDC callback uses. The frontend never sends raw credentials over the
      // WebSocket — STOMP CONNECT only carries the JWT.
      const res = await fetch(apiUrl('/api/login/token'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ email: this.email, password: this.password })
      })
      if (!res.ok) {
        this.displayError(await this.readError(res, 'Invalid credentials'))
        this.password = ''
        return
      }
      const data = await res.json() as { token: string }
      await this.userState.loginWithToken(data.token)
      const referer = (this.$route.query.referer as string | undefined) || '/applications'
      await CONTINUUM_UI.navigate(referer)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Invalid credentials'
      this.displayError(message)
      this.password = ''
    } finally {
      this.loading = false
    }
  }

  resetToEmail() {
    this.password = ''
    this.step = 'email'
    this.$nextTick(() => this.focusEmailInput())
  }

  providerLabel(provider: string): string {
    // Light pretty-printer; full branding lives elsewhere.
    return provider.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
  }

  apiUrl(path: string): string { return apiUrl(path) }

  private focusEmailInput() {
    const ref = this.$refs.emailInput as any
    ref?.$el?.focus?.() ?? ref?.focus?.()
  }

  private focusPasswordInput() {
    const ref = this.$refs.passwordInput as any
    const inner = ref?.$el?.querySelector('input[type="password"]') ?? ref?.$el?.querySelector('input')
    inner?.focus?.()
  }

  private async readError(res: Response, fallback: string): Promise<string> {
    try {
      const body = await res.json()
      return body?.error ?? fallback
    } catch {
      return fallback
    }
  }

  private displayError(text: string) {
    this.toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
  }
}
</script>
