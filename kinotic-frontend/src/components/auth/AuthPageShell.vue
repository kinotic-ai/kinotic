<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="backgroundArt" alt="" class="login-art__image" />
      </aside>

      <main class="login-panel">
        <button v-if="showThemeToggle" type="button" class="login-theme-toggle" @click="toggleTheme"
                :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'">
          <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
        </button>
        <div class="login-panel__content">
          <img :src="brandMark" alt="Kinotic" class="login-brand" />
          <slot />
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
import { Component, Prop, Vue } from 'vue-facing-decorator'
import Toast from 'primevue/toast'

import loginBgDark from '@/assets/left_background_dark.png'
import loginBgLight from '@/assets/left-background_light.png'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'
import '@/pages/auth-pages.css'

/**
 * Page chrome shared by every unauthenticated auth page: background art, theme toggle,
 * brand mark, footer, and the Toast outlet. Page content renders in the default slot.
 */
@Component({
  components: { Toast }
})
export default class AuthPageShell extends Vue {
  /** Overrides the theme-aware background art when set. */
  @Prop({ default: null }) art!: string | null

  @Prop({ default: true }) showThemeToggle!: boolean

  get backgroundArt() { return this.art ?? (darkMode.value ? loginBgDark : loginBgLight) }
  get brandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }
  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }
}
</script>
