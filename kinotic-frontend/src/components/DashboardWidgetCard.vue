<script lang="ts" setup>
import { computed } from 'vue'
import { isDark as darkMode } from '@/composables/useTheme'

defineProps<{ title: string; preview: string; draggable?: boolean }>()

const isDark = computed(() => darkMode.value)
</script>

<template>
  <div
    :class="['relative select-none rounded-lg border p-4', isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0']"
  >
    <h3 :class="['mb-2 text-sm font-semibold', isDark ? 'text-white' : 'text-surface-800']">{{ title }}</h3>
    <div :class="['min-h-[40px] text-sm', isDark ? 'text-surface-400' : 'text-surface-500']">{{ preview }}</div>
  </div>
</template>

<style scoped>
</style>


