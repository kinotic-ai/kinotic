<template>
  <div class="inline-flex flex-wrap items-center gap-1 self-start rounded-lg border border-surface-200 bg-surface-50 p-1 dark:border-surface-700 dark:bg-surface-800/60">
    <button
      v-for="chip in chips"
      :key="chip.value ?? 'all'"
      type="button"
      :aria-pressed="chip.value === modelValue"
      :class="[
        'flex items-center gap-2 whitespace-nowrap rounded-md px-2.5 py-1 text-[0.8125rem] transition-colors',
        chip.value === modelValue
          ? 'bg-surface-0 font-medium text-surface-950 shadow-sm ring-1 ring-surface-200 dark:bg-surface-900 dark:text-surface-0 dark:ring-surface-700'
          : 'text-surface-600 hover:bg-surface-100 hover:text-surface-950 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-0'
      ]"
      @click="emit('update:modelValue', chip.value)"
    >
      <span v-if="chip.severity" :class="['h-2 w-2 shrink-0 rounded-full', SEVERITY_FILL[chip.severity] ?? SEVERITY_FILL.secondary]" aria-hidden="true" />
      {{ chip.label }}
      <span class="rounded-md bg-surface-100 px-1.5 text-xs font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ chip.count }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { SEVERITY_FILL } from '@/util/severity'

/** One chip: a state and how many items are in it; a null value stands for every state. */
export interface StatusChip {
  label: string
  value: string | null
  count: number
  /** The state's status, as a Tag severity, which colours its dot; the All chip has none. */
  severity?: string
}

/** A segmented row of state filters, each with its status dot and count; the selected value is the model. */
defineProps<{
  chips: StatusChip[]
  modelValue: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()
</script>
