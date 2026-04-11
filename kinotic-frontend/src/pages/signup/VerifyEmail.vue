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
            <!-- Loading state -->
            <div v-if="verifying" class="verify-state">
              <div class="login-spinner"></div>
              <h2 class="verify-title">Verifying your email...</h2>
              <p class="verify-text">Please wait while we confirm your account.</p>
            </div>

            <!-- Success state -->
            <div v-else-if="success" class="verify-state">
              <span class="pi pi-check-circle verify-icon verify-icon--success"></span>
              <h2 class="verify-title">Email verified!</h2>
              <p class="verify-text">Your organization has been created. You can now sign in.</p>
              <Button
                label="Sign in"
                class="login-submit"
                @click="$router.push('/login')"
              />
            </div>

            <!-- Error state -->
            <div v-else class="verify-state">
              <span class="pi pi-times-circle verify-icon verify-icon--error"></span>
              <h2 class="verify-title">Verification failed</h2>
              <p class="verify-text">{{ errorMessage }}</p>
              <Button
                label="Sign up again"
                class="login-submit"
                @click="$router.push('/signup')"
              />
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
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator';
import Button from 'primevue/button'

import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'

@Component({
  components: {
    Button,
  }
})
export default class VerifyEmail extends Vue {
  private readonly loginBackgroundArt = loginPageLeft

  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  verifying = true
  success = false
  errorMessage = ''

  async mounted() {
    const token = this.$route.query.token as string

    if (!token) {
      this.verifying = false
      this.errorMessage = 'No verification token provided.'
      return
    }

    try {
      const response = await fetch(`/api/signup/verify?token=${encodeURIComponent(token)}`)
      const data = await response.json()

      if (response.ok) {
        this.success = true
      } else {
        this.errorMessage = data.error || 'Verification failed.'
      }
    } catch (error: unknown) {
      this.errorMessage = error instanceof Error ? error.message : 'Verification failed.'
    } finally {
      this.verifying = false
    }
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
}

.verify-text {
  margin: 0.5rem 0 1.5rem;
  line-height: 1.5;
  color: var(--p-text-muted-color);
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
