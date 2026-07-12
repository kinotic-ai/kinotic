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

<script setup lang="ts">
import { computed } from 'vue'
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
const props = withDefaults(defineProps<{
  /** Overrides the theme-aware background art when set. */
  art?: string | null

  showThemeToggle?: boolean
}>(), {
  art: null,
  showThemeToggle: true,
})

const backgroundArt = computed(() => props.art ?? (darkMode.value ? loginBgDark : loginBgLight))
const brandMark = computed(() => darkMode.value ? loginPageLogo : loginPageLogoLight)
const isDark = darkMode
function toggleTheme() { toggleDark() }
</script>
