<template>
  <HeaderIconButton
    :label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
    :tooltip="isDark ? 'Light mode' : 'Dark mode'"
    @click="toggleDark()"
  >
    <component :is="isDark ? Sun : Moon" :size="18" :stroke-width="1.75" aria-hidden="true" />
  </HeaderIconButton>
</template>

<script setup lang="ts">
import { Moon, Sun } from '@lucide/vue'
import { isDark, toggleDark } from '../composables/useTheme'
import HeaderIconButton from './HeaderIconButton.vue'

/** The header's light / dark mode switch. */
</script>
