<template>
  <!-- The GraphQL mark (a hexagon of nodes around a triangle) redrawn as a line icon, so it sits
       beside the app's Lucide icons at the same size and stroke weight -->
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
    <path d="M12 3.5 19.4 7.75v8.5L12 20.5 4.6 16.25v-8.5Z" />
    <path d="M12 3.5 19.4 16.25H4.6Z" />
    <circle cx="12" cy="3.5" r="1.4" fill="currentColor" />
    <circle cx="19.4" cy="7.75" r="1.4" fill="currentColor" />
    <circle cx="19.4" cy="16.25" r="1.4" fill="currentColor" />
    <circle cx="12" cy="20.5" r="1.4" fill="currentColor" />
    <circle cx="4.6" cy="16.25" r="1.4" fill="currentColor" />
    <circle cx="4.6" cy="7.75" r="1.4" fill="currentColor" />
  </svg>
</template>

<script setup lang="ts">
/** The GraphQL mark as a line icon, sized and stroked like a Lucide icon. */
withDefaults(defineProps<{
  size?: number
  strokeWidth?: number
}>(), {
  size: 24,
  strokeWidth: 2
})
</script>
