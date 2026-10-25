<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="branding.art" alt="" class="login-art__image" />
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

<script setup lang="ts">
import { computed, inject } from 'vue'
import Toast from 'primevue/toast'

import loginPageLogoLight from '../assets/login-page-kinotic-logo-light.svg'
import { AUTH_PAGE_BRANDING } from '../bootstrap'
import { isDark as darkMode, toggleDark } from '../composables/useTheme'
import '../styles/auth-pages.css'

/**
 * Page chrome shared by every unauthenticated auth page: the art and brand mark of the app's
 * AuthPageBranding, theme toggle, footer, and the Toast outlet. Page content renders in the
 * default slot.
 */
withDefaults(defineProps<{
  showThemeToggle?: boolean
}>(), {
  showThemeToggle: true,
})

const branding = inject(AUTH_PAGE_BRANDING)!
const brandMark = computed(() => darkMode.value ? branding.darkBrandMark : loginPageLogoLight)
const isDark = darkMode
function toggleTheme() { toggleDark() }
</script>
