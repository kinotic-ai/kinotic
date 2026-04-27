<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="loginBackgroundArt" alt="" class="login-art__image" />
      </aside>

      <main class="login-panel">
        <button type="button" class="login-theme-toggle" @click="toggleTheme" :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'">
          <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
        </button>
        <div class="login-panel__content">
          <img :src="loginBrandMark" alt="Kinotic" class="login-brand" />

          <div class="login-form">
            <!-- Password form -->
            <div v-if="!completed" class="login-form__step">
              <div class="verify-header">
                <span class="verify-icon-wrap verify-icon-wrap--primary">
                  <span class="pi pi-shield verify-header__icon"></span>
                </span>
                <h2 class="verify-title">Thank you for verifying your email</h2>
                <p class="verify-text">Please set your password to finish creating your account.</p>
              </div>

              <IconField class="login-field">
                <Password
                  ref="passwordInput"
                  v-model="request.password"
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
                  @keyup.enter="handleSubmit"
                />
              </IconField>

              <Button
                label="Create account"
                class="login-submit"
                :loading="loading"
                :disabled="!canSubmit"
                @click="handleSubmit"
              />
            </div>

            <!-- Success state -->
            <div v-else class="verify-state">
              <span class="verify-icon-wrap verify-icon-wrap--success">
                <span class="pi pi-check verify-header__icon"></span>
              </span>
              <h2 class="verify-title">Account created!</h2>
              <p class="verify-text">Your organization is ready. You can now sign in.</p>
              <Button
                label="Sign in"
                class="login-submit"
                @click="$router.push('/login')"
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
import { Component, Vue } from 'vue-facing-decorator';
import Password from 'primevue/password'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import type { SignUpCompleteRequest } from '@kinotic-ai/os-api'

import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import { apiUrl } from '@/util/helpers'
import '@/pages/auth-pages.css'

@Component({
  components: {
    Password,
    Button,
    IconField,
    Toast,
  }
})
export default class VerifyEmail extends Vue {
  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  // Stays empty (no border tint) until the user types into the confirm field.
  // Once non-empty: green if it matches the password, red otherwise.
  get confirmStateClass(): string {
    if (!this.confirmPassword) return ''
    return this.request.password === this.confirmPassword
      ? 'login-password--match'
      : 'login-password--mismatch'
  }

  get canSubmit(): boolean {
    return !!this.request.password
        && !!this.confirmPassword
        && this.request.password === this.confirmPassword
  }

  request: SignUpCompleteRequest = {
    token: '',
    password: '',
  }
  confirmPassword = ''
  loading = false
  completed = false

  mounted() {
    this.request.token = (this.$route.query.token as string) || ''
    if (!this.request.token) {
      this.displayAlert('No verification token provided.')
    }
  }

  private focusConfirm() {
    const el = this.$refs.confirmPasswordInput as any
    if (el?.$el) {
      el.$el.querySelector('input')?.focus()
    }
  }

  async handleSubmit() {
    if (!this.request.token) {
      this.displayAlert('No verification token provided.')
      return
    }
    if (!this.request.password) {
      this.displayAlert('Password is required')
      return
    }
    if (this.request.password !== this.confirmPassword) {
      this.displayAlert('Passwords do not match')
      return
    }

    this.loading = true
    try {
      const response = await fetch(apiUrl('/api/signup/complete'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(this.request),
      })

      const data = await response.json()

      if (!response.ok) {
        this.displayAlert(data.error || 'Account creation failed')
        return
      }

      this.completed = true
    } catch (error: unknown) {
      this.displayAlert(error instanceof Error ? error.message : 'Account creation failed')
    } finally {
      this.loading = false
    }
  }

  private displayAlert(text: string) {
    this.toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 10000
    })
  }
}
</script>

<style scoped>
/* Confirm-password border tint: red while passwords don't match, green once they do.
 * Empty state stays neutral so the user isn't yelled at before they've typed anything. */
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

.verify-state {
  text-align: center;
  padding: 1rem 0 0.5rem;
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

.verify-icon-wrap--success {
  background: color-mix(in srgb, var(--p-green-500) 14%, transparent);
}

.verify-header__icon {
  font-size: 2rem;
}

.verify-icon-wrap--primary .verify-header__icon {
  color: var(--p-primary-color);
}

.verify-icon-wrap--success .verify-header__icon {
  color: var(--p-green-500);
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
