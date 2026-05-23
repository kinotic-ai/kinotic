<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="max-w-[1200px]">
    <div class="mb-2 text-sm text-surface-500">Organization</div>
    <h1 class="mb-3 text-2xl font-semibold text-surface-950 dark:text-surface-0">{{ title }}</h1>
    <p class="max-w-[560px] text-sm text-surface-500 dark:text-surface-400">
      {{ description || 'This section is being prepared.' }}
    </p>
  </div>
</template>