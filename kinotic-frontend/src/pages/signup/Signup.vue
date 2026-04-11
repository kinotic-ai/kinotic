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

          <div v-if="!submitted" class="login-form">
            <h2 class="signup-title">Create your organization</h2>

            <div class="login-form__step">
              <InputText
                v-model="orgName"
                class="login-input"
                placeholder="Organization name"
                @keyup.enter="focusNext('orgDescription')"
              />

              <InputText
                ref="orgDescription"
                v-model="orgDescription"
                class="login-input"
                placeholder="Organization description (optional)"
                @keyup.enter="focusNext('email')"
              />

              <InputText
                ref="email"
                v-model="email"
                class="login-input"
                placeholder="Your email"
                type="email"
                @keyup.enter="focusNext('displayName')"
              />

              <InputText
                ref="displayName"
                v-model="displayName"
                class="login-input"
                placeholder="Your name"
                @keyup.enter="focusNext('password')"
              />

              <Password
                ref="password"
                v-model="password"
                class="login-input"
                placeholder="Password"
                :feedback="false"
                toggleMask
                @keyup.enter="focusNext('confirmPassword')"
              />

              <Password
                ref="confirmPassword"
                v-model="confirmPassword"
                class="login-input"
                placeholder="Confirm password"
                :feedback="false"
                toggleMask
                @keyup.enter="handleSubmit"
              />

              <Button
                label="Sign Up"
                class="login-submit"
                :loading="loading"
                @click="handleSubmit"
              />
            </div>

            <div class="signup-footer-link">
              Already have an account? <router-link to="/login" class="login-link">Sign in</router-link>
            </div>
          </div>

          <div v-else class="login-form">
            <div class="signup-success">
              <span class="pi pi-envelope signup-success__icon"></span>
              <h2 class="signup-title">Check your email</h2>
              <p class="signup-success__text">
                We've sent a verification link to <strong>{{ email }}</strong>.
                Click the link to activate your organization.
              </p>
              <p class="signup-success__text signup-success__text--muted">
                The link expires in 24 hours.
              </p>
            </div>
          </div>

          <footer class="login-footer">
            <a href="#" class="login-footer__link">Terms of use</a>
            <span class="login-footer__divider">|</span>
            <a href="#" class="login-footer__link">Privacy policy</a>
          </footer>
        </div>
      </main>
    </div>

    <Toast />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator';
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'

import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'

@Component({
  components: {
    InputText,
    Password,
    Button,
    Toast,
  }
})
export default class Signup extends Vue {
  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  orgName = ''
  orgDescription = ''
  email = ''
  displayName = ''
  password = ''
  confirmPassword = ''
  loading = false
  submitted = false

  private focusNext(refName: string) {
    const el = this.$refs[refName] as any
    if (el?.$el) {
      el.$el.querySelector('input')?.focus()
    } else if (el?.focus) {
      el.focus()
    }
  }

  async handleSubmit() {
    if (!this.orgName.trim()) {
      this.displayAlert('Organization name is required')
      return
    }
    if (!this.email.trim() || !this.email.includes('@')) {
      this.displayAlert('Please enter a valid email address')
      return
    }
    if (!this.displayName.trim()) {
      this.displayAlert('Your name is required')
      return
    }
    if (!this.password) {
      this.displayAlert('Password is required')
      return
    }
    if (this.password !== this.confirmPassword) {
      this.displayAlert('Passwords do not match')
      return
    }

    this.loading = true
    try {
      const response = await fetch('/api/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orgName: this.orgName.trim(),
          orgDescription: this.orgDescription.trim() || null,
          email: this.email.trim(),
          displayName: this.displayName.trim(),
          password: this.password,
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        this.displayAlert(data.error || 'Sign-up failed')
        return
      }

      this.submitted = true
    } catch (error: unknown) {
      this.displayAlert(error instanceof Error ? error.message : 'Sign-up failed')
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
.signup-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  text-align: center;
}

.signup-footer-link {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.875rem;
}

.signup-success {
  text-align: center;
  padding: 2rem 0;
}

.signup-success__icon {
  font-size: 3rem;
  color: var(--p-primary-color);
  margin-bottom: 1rem;
}

.signup-success__text {
  margin: 0.5rem 0;
  line-height: 1.5;
}

.signup-success__text--muted {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}
</style>
