<template>
  <dl class="mt-3 divide-y divide-surface-100 text-sm dark:divide-surface-800">
    <div v-for="fact in facts" :key="fact.label" class="flex items-center gap-3 py-2.5">
      <component :is="fact.icon" :size="16" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
      <dt class="w-28 shrink-0 text-muted-color">{{ fact.label }}</dt>
      <dd :class="['m-0 min-w-0 truncate text-surface-950 dark:text-surface-0', fact.mono ? 'font-mono text-[0.8125rem]' : '']" v-tooltip.top="fact.value">
        <RouterLink v-if="fact.to" :to="fact.to" class="hover:underline">{{ fact.value }}</RouterLink>
        <template v-else>{{ fact.value }}</template>
      </dd>
    </div>
  </dl>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface Fact {
  label: string
  icon: Component
  value: string
  /** Renders the value in the monospace font, for ids and names. */
  mono?: boolean
  /** Makes the value a link inside the portal. */
  to?: string
}

/** The identifying facts of an overview page, one labelled row each, values cut to one line. */
defineProps<{
  facts: Fact[]
}>()
</script>
