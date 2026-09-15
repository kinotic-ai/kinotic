<template>
  <div class="flex flex-wrap items-center gap-1.5">
    <button
      v-for="chip in chips"
      :key="chip.value ?? 'all'"
      type="button"
      :class="[
        'flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-1.5 text-xs leading-none transition-colors',
        chip.value === modelValue
          ? 'border-transparent bg-primary-50 font-medium text-primary-700 dark:bg-primary-400/15 dark:text-primary-300'
          : 'border-surface text-muted-color hover:bg-emphasis hover:text-color'
      ]"
      @click="emit('update:modelValue', chip.value)"
    >
      {{ chip.label }}
      <span class="tabular-nums">{{ chip.count }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
/** One chip: a state and how many items are in it; a null value stands for every state. */
export interface StatusChip {
  label: string
  value: string | null
  count: number
}

/** A row of state filters with their counts; the selected value is the model. */
defineProps<{
  chips: StatusChip[]
  modelValue: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()
</script>
