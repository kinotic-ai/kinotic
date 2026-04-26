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
              <h2 class="verify-title">Thank you for verifying your email</h2>
              <p class="verify-text">Please set your password to finish creating your account.</p>

              <Password
                ref="passwordInput"
                v-model="request.password"
                class="login-input"
                placeholder="Password"
                :feedback="false"
                toggleMask
                @keyup.enter="focusConfirm"
              />

              <Password
                ref="confirmPasswordInput"
                v-model="confirmPassword"
                class="login-input"
                placeholder="Confirm password"
                :feedback="false"
                toggleMask
                @keyup.enter="handleSubmit"
              />

              <Button
                label="Create account"
                class="login-submit"
                :loading="loading"
                @click="handleSubmit"
              />
            </div>

            <!-- Success state -->
            <div v-else class="verify-state">
              <span class="pi pi-check-circle verify-icon verify-icon--success"></span>
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
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import type { SignUpCompleteRequest } from '@kinotic-ai/os-api'

import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import { apiUrl } from '@/util/helpers'

@Component({
  components: {
    Password,
    Button,
    Toast,
  }
})
export default class VerifyEmail extends Vue {
  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

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
.verify-state {
  text-align: center;
  padding: 2rem 0;
}

.verify-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  text-align: center;
}

.verify-text {
  margin: 0.5rem 0 1.5rem;
  line-height: 1.5;
  color: var(--p-text-muted-color);
  text-align: center;
}

.verify-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.verify-icon--success {
  color: var(--p-green-500);
}

.verify-icon--error {
  color: var(--p-red-500);
}
</style>
