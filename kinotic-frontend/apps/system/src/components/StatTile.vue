<template>
  <component
    :is="to ? RouterLink : 'div'"
    :to="to"
    class="flex flex-col gap-1 rounded-lg border border-surface p-4"
    :class="to ? 'cursor-pointer text-color no-underline transition-colors hover:bg-emphasis' : ''"
  >
    <div class="flex items-start justify-between gap-2">
      <span class="text-xs font-medium uppercase tracking-wide text-muted-color">{{ label }}</span>
      <span
        v-if="icon"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
        :style="{ background: accentStyle.tint }"
      >
        <i :class="['pi', icon]" :style="{ color: accentStyle.icon, fontSize: '0.9rem' }" />
      </span>
    </div>
    <span v-if="tag" class="py-1">
      <Tag :value="value" :severity="tag" />
    </span>
    <span v-else class="text-3xl font-semibold">{{ value }}</span>
    <span class="text-xs text-muted-color">{{ description }}</span>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Tag from 'primevue/tag'

import { accentColor, isDark, type ChartAccent } from '@kinotic-ai/frontend-common'

export type StatTileAccent = ChartAccent

/**
 * One dashboard statistic: a labeled value with a caption explaining what it measures.
 * Given a route it becomes a link with hover affordance; given a tag severity the value
 * renders as a Tag instead of a number; given an icon and accent it wears a tinted icon
 * chip — the value itself always stays in text ink.
 */
const props = defineProps<{
  label: string
  value: string
  description: string
  tag?: string
  to?: RouteLocationRaw
  icon?: string
  accent?: StatTileAccent
}>()

function withAlpha(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

// The accent for the icon, and the same hue at low alpha for the chip behind it
const accentStyle = computed(() => {
  const color = accentColor(props.accent ?? 'sky', isDark.value)
  return { icon: color, tint: withAlpha(color, isDark.value ? 0.16 : 0.12) }
})
</script>
