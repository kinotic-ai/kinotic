<template>
  <!-- Three rounded tiles and a round one in a 2×2 grid: the runs an organization executes -->
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <rect x="3.5" y="3.5" width="7" height="7" rx="1.75" />
    <rect x="13.5" y="3.5" width="7" height="7" rx="1.75" />
    <circle cx="7" cy="17" r="3.5" />
    <rect x="13.5" y="13.5" width="7" height="7" rx="1.75" />
  </svg>
</template>

<script setup lang="ts">
/** The jobs mark as a line icon, sized and stroked like a Lucide icon. */
withDefaults(defineProps<{
  size?: number
  strokeWidth?: number
}>(), {
  size: 24,
  strokeWidth: 2
})
</script>
