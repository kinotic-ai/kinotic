<template>
  <component
    :is="href ? 'a' : 'button'"
    v-bind="href ? { href, target: '_blank', rel: 'noopener' } : { type: 'button' }"
    class="flex h-9 w-9 items-center justify-center rounded-full border border-surface-200 bg-transparent text-surface-600 transition-colors hover:border-surface-300 hover:text-surface-950 dark:border-surface-800 dark:text-surface-400 dark:hover:border-surface-700 dark:hover:text-surface-0"
    :aria-label="label"
    v-tooltip.bottom="tooltip"
  >
    <slot />
  </component>
</template>

<script setup lang="ts">
/**
 * A round outlined icon button in the header's action row: a button, or a link opening
 * {@code href} in a new tab when set. The icon renders in the default slot.
 */
defineProps<{
  /** The accessible name. */
  label: string
  tooltip: string
  href?: string
}>()
</script>
