<script setup lang="ts">
import { Check, Circle, LoaderCircle } from '@lucide/vue'
import type { CreationPhase } from '@/composables/useCreationPhase'
import characterUrl from '@/assets/kinotic-character-tile.svg'

/**
 * The Kinotic tile under a create drawer's form. While filling in the form it shows the hint;
 * while creating and once ready it names what is being set up and lists each step's progress.
 */
defineProps<{
  phase: CreationPhase
  /** The name of what is being created. */
  name: string
  hint: string
  /** Shown under the heading once ready, pointing at what to do next. */
  nextStep: string
  steps: {
    label: string
    done: boolean
    /** The step the request is working on; later steps wait for it. */
    active: boolean
  }[]
}>()
</script>

<template>
  <div class="flex flex-1 flex-col items-center justify-center gap-5 py-8 text-center">
    <img :src="characterUrl" alt="" class="h-48 w-48 dark:drop-shadow-[0_0_32px_rgb(40_254_180/0.28)]" />

    <p v-if="phase === 'form'" class="max-w-xs text-sm text-surface-500 dark:text-surface-400">{{ hint }}</p>

    <div v-else class="flex flex-col items-center gap-4" aria-live="polite">
      <p class="text-base font-medium text-surface-950 dark:text-surface-0">
        {{ phase === 'ready' ? `${name} is ready` : `Setting up ${name}` }}
      </p>
      <p v-if="phase === 'ready'" class="-mt-2 text-sm text-surface-500 dark:text-surface-400">{{ nextStep }}</p>
      <ul class="flex flex-col gap-2 text-left">
        <li
          v-for="step in steps"
          :key="step.label"
          :class="['flex items-center gap-2.5 font-mono text-[0.8125rem]', step.done ? 'text-surface-800 dark:text-surface-100' : 'text-surface-500 dark:text-surface-400']"
        >
          <Check v-if="step.done" :size="16" :stroke-width="2" class="text-green-600 dark:text-green-400" aria-hidden="true" />
          <LoaderCircle v-else-if="step.active" :size="16" :stroke-width="2" class="animate-spin" aria-hidden="true" />
          <Circle v-else :size="16" :stroke-width="2" class="opacity-40" aria-hidden="true" />
          {{ step.label }}
        </li>
      </ul>
    </div>
  </div>
</template>
