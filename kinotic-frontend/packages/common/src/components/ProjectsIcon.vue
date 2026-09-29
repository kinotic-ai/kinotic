<template>
  <!-- Two stacked lids over a rounded container: a set of projects kept together -->
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
    <path d="M7 3.5h10" />
    <path d="M5 7.5h14" />
    <path d="M4 11.5h16v5a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4Z" />
  </svg>
</template>

<script setup lang="ts">
/** The projects mark as a line icon, sized and stroked like a Lucide icon. */
withDefaults(defineProps<{
  size?: number
  strokeWidth?: number
}>(), {
  size: 24,
  strokeWidth: 2
})
</script>
