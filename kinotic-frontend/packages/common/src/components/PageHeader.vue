<script setup lang="ts">
/**
 * The title block a page opens with: the page's name, an optional sentence saying what the
 * page is for, and an {@code actions} slot for the controls that act on the whole page.
 *
 * The section a page belongs to is named by the sidebar, so the title stands alone here.
 * A drill-down page (one item opened from a list the sidebar names) fills the
 * {@code eyebrow} slot with the way back to that list.
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="mb-6 flex flex-wrap items-start justify-between gap-4">
    <div class="min-w-0">
      <div v-if="$slots.eyebrow" class="mb-2 flex items-center gap-1.5 text-sm text-surface-500 dark:text-surface-400">
        <slot name="eyebrow" />
      </div>
      <h1 class="text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0">{{ title }}</h1>
      <p v-if="description" class="mt-2 max-w-[640px] text-sm text-surface-500 dark:text-surface-400">
        {{ description }}
      </p>
    </div>

    <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
