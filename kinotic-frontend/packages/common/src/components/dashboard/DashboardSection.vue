<template>
  <section class="flex flex-col gap-3">
    <div>
      <h2 class="text-lg font-semibold tracking-tight text-surface-950 dark:text-surface-0">{{ title }}</h2>
      <p v-if="description" class="text-sm text-surface-500 dark:text-surface-400">{{ description }}</p>
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
/**
 * One titled band of an overview page: a heading naming what the band covers, an optional
 * sentence saying where its figures come from, and the band's tiles and cards in the default
 * slot.
 */
defineProps<{
  title: string
  description?: string
}>()
</script>
