<template>
  <component
    :is="to ? RouterLink : 'div'"
    :to="to"
    class="flex flex-col gap-1 rounded-lg border border-surface p-4"
    :class="to ? 'cursor-pointer text-color no-underline transition-colors hover:bg-emphasis' : ''"
  >
    <span class="text-xs font-medium uppercase tracking-wide text-muted-color">{{ label }}</span>
    <span v-if="tag" class="py-1">
      <Tag :value="value" :severity="tag" />
    </span>
    <span v-else class="text-3xl font-semibold">{{ value }}</span>
    <span class="text-xs text-muted-color">{{ description }}</span>
  </component>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Tag from 'primevue/tag'

/**
 * One dashboard statistic: a labeled value with a caption explaining what it measures.
 * Given a route it becomes a link with hover affordance; given a tag severity the value
 * renders as a Tag instead of a number.
 */
defineProps<{
  label: string
  value: string
  description: string
  tag?: string
  to?: RouteLocationRaw
}>()
</script>
