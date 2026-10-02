<script setup lang="ts">
/**
 * The title block a page opens with: the page's name, an optional sentence saying what the
 * page is for, and an {@code actions} slot for the controls that act on the whole page.
 *
 * The section a page belongs to is named by the sidebar, so the title stands alone here.
 * A drill-down page (one item opened from a list the sidebar names) fills the
 * {@code eyebrow} slot with the way back to that list, and the {@code status} slot with the
 * item's current state, shown beside the title.
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
      <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h1 class="text-[1.875rem] font-medium leading-9 tracking-[-0.01em] text-surface-950 dark:text-surface-0">{{ title }}</h1>
        <div v-if="$slots.status" class="flex items-center gap-2">
          <slot name="status" />
        </div>
      </div>
      <p v-if="description" class="mt-3 max-w-[800px] text-[0.9375rem] leading-[1.375rem] text-surface-600 dark:text-surface-400">
        {{ description }}
      </p>
    </div>

    <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
