<template>
  <dl class="mt-3 divide-y divide-surface-100 text-sm dark:divide-surface-800">
    <div v-for="fact in shown" :key="fact.label" class="flex items-center gap-3 py-2.5">
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
import { computed, type Component } from 'vue'

interface Fact {
  label: string
  icon: Component
  /** The value to show; a fact without one, null, is left out of the list. */
  value: string | null
  /** Renders the value in the monospace font, for ids and names. */
  mono?: boolean
  /** Makes the value a link inside the portal. */
  to?: string
}

/** The identifying facts of an overview page, one labelled row each, values cut to one line. */
const props = defineProps<{
  facts: Fact[]
}>()

const shown = computed(() => props.facts.filter((fact): fact is Fact & { value: string } => fact.value !== null))
</script>
