<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="loginBackgroundArt" alt="" class="login-art__image" />
      </aside>

      <main class="login-panel">
        <button
          type="button"
          class="login-theme-toggle"
          @click="toggleTheme"
          :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
        >
          <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
        </button>
        <div class="login-panel__content">
          <img :src="loginBrandMark" alt="Kinotic" class="login-brand" />

          <div class="login-form">
            <!-- Loading the invitation -->
            <div v-if="loading" class="login-form__step">
              <div class="verify-header">
                <span class="verify-icon-wrap verify-icon-wrap--primary">
                  <span class="pi pi-spin pi-spinner verify-header__icon"></span>
                </span>
                <h2 class="verify-title">Loading your invitation…</h2>
              </div>
            </div>

            <!-- Invalid / expired invitation -->
            <div v-else-if="loadError" class="login-form__step">
              <div class="verify-header">
                <span class="verify-icon-wrap verify-icon-wrap--primary">
                  <span class="pi pi-exclamation-triangle verify-header__icon"></span>
                </span>
                <h2 class="verify-title">Invitation unavailable</h2>
                <p class="verify-text">{{ loadError }}</p>
              </div>
            </div>

            <!-- LOCAL: set a password -->
            <div v-else-if="isLocal" class="login-form__step">
              <div class="verify-header">
                <span class="verify-icon-wrap verify-icon-wrap--primary">
                  <span class="pi pi-user-plus verify-header__icon"></span>
                </span>
                <h2 class="verify-title">Join {{ organizationName }}</h2>
                <p class="verify-text">Set a password for {{ email }} to accept your invitation.</p>
              </div>

              <IconField class="login-field">
                <Password
                  ref="passwordInput"
                  v-model="password"
                  class="login-password"
                  input-class="login-password-input"
                  placeholder="Password"
                  :feedback="false"
                  toggleMask
                  @keyup.enter="focusConfirm"
                />
              </IconField>

              <IconField class="login-field">
                <Password
                  ref="confirmPasswordInput"
                  v-model="confirmPassword"
                  :class="['login-password', confirmStateClass]"
                  input-class="login-password-input"
                  placeholder="Confirm password"
                  :feedback="false"
                  toggleMask
                  @keyup.enter="handleLocalSubmit"
                />
              </IconField>

              <Button
                label="Accept invitation"
                class="login-submit"
                :loading="submitting"
                :disabled="!canSubmitLocal"
                @click="handleLocalSubmit"
              />
            </div>

            <!-- OIDC: sign in with the configured provider -->
            <div v-else class="login-form__step">
              <div class="verify-header">
                <span class="verify-icon-wrap verify-icon-wrap--primary">
                  <span class="pi pi-user-plus verify-header__icon"></span>
                </span>
                <h2 class="verify-title">Join {{ organizationName }}</h2>
                <p class="verify-text">Sign in to accept your invitation for {{ email }}.</p>
              </div>

              <Button
                :label="`Sign in with ${providerName}`"
                class="login-submit"
                :loading="submitting"
                @click="handleOidcSignIn"
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
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { AuthType } from '@kinotic-ai/os-api'

import loginBgDark from '@/assets/left_background_dark.png'
import loginBgLight from '@/assets/left-background_light.png'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import { apiUrl } from '@/util/helpers'
import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'
import '@/pages/auth-pages.css'

interface InviteDetails {
  email: string
  displayName: string | null
  organizationName: string | null
  authType: AuthType
  oidcProviderName: string | null
}

@Component({
  components: {
    Password,
    Button,
    IconField,
    Toast,
  }
})
export default class InviteAccept extends Vue {
  private toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  private token = ''
  loading = true
  loadError = ''
  invite: InviteDetails | null = null

  password = ''
  confirmPassword = ''
  submitting = false

  get loginBackgroundArt() { return darkMode.value ? loginBgDark : loginBgLight }
  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  get isLocal(): boolean { return this.invite?.authType === AuthType.LOCAL }
  get email(): string { return this.invite?.email ?? '' }
  get organizationName(): string { return this.invite?.organizationName ?? 'Kinotic' }
  get providerName(): string { return this.invite?.oidcProviderName ?? 'SSO' }

  get confirmStateClass(): string {
    if (!this.confirmPassword) return ''
    return this.password === this.confirmPassword ? 'login-password--match' : 'login-password--mismatch'
  }

  get canSubmitLocal(): boolean {
    return !!this.password && !!this.confirmPassword && this.password === this.confirmPassword
  }

  async mounted() {
    this.token = (this.$route.query.token as string) || ''
    if (!this.token) {
      this.loading = false
      this.loadError = 'No invitation token provided.'
      return
    }
    await this.fetchInvite()
  }

  private async fetchInvite() {
    try {
      const response = await fetch(apiUrl('/api/invite?token=' + encodeURIComponent(this.token)), {
        credentials: 'include',
      })
      if (!response.ok) {
        this.loadError = await this.readError(response, 'This invitation is invalid or has expired.')
        return
      }
      this.invite = await response.json()
    } catch (error: unknown) {
      this.loadError = error instanceof Error ? error.message : 'This invitation is invalid or has expired.'
    } finally {
      this.loading = false
    }
  }

  private focusConfirm() {
    const el = this.$refs.confirmPasswordInput as any
    el?.$el?.querySelector('input')?.focus()
  }

  async handleLocalSubmit() {
    if (!this.canSubmitLocal) {
      this.displayAlert('Passwords do not match')
      return
    }
    this.submitting = true
    try {
      const response = await fetch(apiUrl('/api/invite/accept'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ token: this.token, password: this.password }),
      })
      if (!response.ok) {
        this.displayAlert(await this.readError(response, 'Failed to accept invitation'))
        return
      }
      await this.userState.login()
      await CONTINUUM_UI.navigate('/applications')
    } catch (error: unknown) {
      this.displayAlert(error instanceof Error ? error.message : 'Failed to accept invitation')
    } finally {
      this.submitting = false
    }
  }

  async handleOidcSignIn() {
    this.submitting = true
    try {
      const response = await fetch(apiUrl('/api/invite/start'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ token: this.token }),
      })
      if (!response.ok) {
        this.displayAlert(await this.readError(response, 'Failed to start sign-in'))
        return
      }
      const body = await response.json()
      if (body?.redirect) {
        window.location.href = body.redirect
      } else {
        this.displayAlert('Failed to start sign-in')
      }
    } catch (error: unknown) {
      this.displayAlert(error instanceof Error ? error.message : 'Failed to start sign-in')
    } finally {
      this.submitting = false
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

  private displayAlert(text: string) {
    this.toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
  }
}
</script>

<style scoped>
.login-password--mismatch :deep(.login-password-input),
.login-password--match :deep(.login-password-input) {
  transition: border-color 0.18s ease;
}

.login-password--mismatch :deep(.login-password-input) {
  border-color: var(--p-red-500);
}

.login-password--match :deep(.login-password-input) {
  border-color: var(--p-green-500);
}

.verify-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 999px;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap--primary {
  background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
}

.verify-header__icon {
  font-size: 2rem;
}

.verify-icon-wrap--primary .verify-header__icon {
  color: var(--p-primary-color);
}

.verify-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.5rem;
  text-align: center;
  line-height: 1.25;
}

.verify-text {
  margin: 0.25rem 0 1.5rem;
  line-height: 1.5;
  color: var(--p-text-muted-color);
  text-align: center;
}
</style>
