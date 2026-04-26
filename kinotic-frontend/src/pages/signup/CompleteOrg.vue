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

          <div v-if="!token" class="login-form">
            <div class="signup-error">
              <span class="pi pi-exclamation-triangle signup-error__icon"></span>
              <h2 class="signup-title">Missing registration token</h2>
              <p class="signup-success__text">
                Open this page from the link your identity provider sent you, or
                <router-link to="/signup" class="login-link">start a new sign-up</router-link>.
              </p>
            </div>
          </div>

          <div v-else class="login-form">
            <h2 class="signup-title">Name your organization</h2>
            <p class="login-form__subtitle">
              Welcome! Pick a name for your new organization to finish creating your account.
            </p>

            <div class="login-form__step">
              <InputText
                ref="orgName"
                v-model="orgName"
                class="login-input"
                placeholder="Organization name"
                @keyup.enter="focusNext('orgDescription')"
              />

              <InputText
                ref="orgDescription"
                v-model="orgDescription"
                class="login-input"
                placeholder="Description (optional)"
                @keyup.enter="handleSubmit"
              />

              <Button
                label="Create organization"
                class="login-submit"
                :loading="loading"
                @click="handleSubmit"
              />
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
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'

import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'
import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import { apiUrl } from '@/util/helpers'
import '@/pages/auth-pages.css'

/**
 * Lands here after `/api/signup/callback/:configId` redirects with `?token=<verificationToken>`
 * (a {@code PendingRegistration}). The user supplies an org name; we POST to
 * `/api/signup/complete-org`, the backend creates the Organization + admin IamUser, and
 * returns a Kinotic JWT we then use to open the STOMP session.
 */
@Component({
  components: { InputText, Button, Toast }
})
export default class CompleteOrg extends Vue {
  orgName = ''
  orgDescription = ''
  loading = false

  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  get token(): string | null {
    const t = this.$route.query.token ?? new URLSearchParams(window.location.search).get('token')
    return typeof t === 'string' && t.length > 0 ? t : null
  }

  mounted() {
    if (this.token) this.$nextTick(() => this.focusNext('orgName'))
  }

  focusNext(refName: string) {
    const el = this.$refs[refName] as any
    el?.$el?.querySelector?.('input')?.focus?.() ?? el?.focus?.()
  }

  async handleSubmit() {
    if (!this.token) return
    const orgName = this.orgName.trim()
    if (!orgName) {
      this.displayError('Organization name is required')
      return
    }

    this.loading = true
    try {
      const res = await fetch(apiUrl('/api/signup/complete-org'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({
          token: this.token,
          orgName,
          orgDescription: this.orgDescription.trim() || null,
        })
      })
      if (!res.ok) {
        const message = await this.readError(res, 'Could not create organization')
        this.displayError(message)
        return
      }
      const data = await res.json() as { token: string }
      await this.userState.loginWithToken(data.token)
      await CONTINUUM_UI.navigate('/applications')
    } catch (err) {
      this.displayError(err instanceof Error ? err.message : 'Sign-up failed')
    } finally {
      this.loading = false
    }
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

<style scoped>
.signup-error {
  text-align: center;
  padding: 2rem 0;
}

.signup-error__icon {
  font-size: 3rem;
  color: var(--p-primary-500);
  margin-bottom: 1rem;
}
</style>
