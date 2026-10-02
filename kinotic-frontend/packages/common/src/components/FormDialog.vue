<template>
  <Dialog :visible="visible" modal :showHeader="false" :pt="DIALOG_PT" @update:visible="emit('update:visible', $event)">
    <form class="flex flex-col gap-6 p-6" @submit.prevent="emit('submit')">
      <div class="flex items-start gap-4">
        <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-surface-100 text-surface-700 dark:bg-surface-800 dark:text-surface-200">
          <component :is="icon" :size="20" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="min-w-0 flex-1 pt-0.5">
          <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">{{ title }}</h2>
          <p v-if="description" class="mt-0.5 text-sm text-muted-color">{{ description }}</p>
        </div>
        <button type="button"
                class="-mr-1 -mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-950 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-0"
                aria-label="Close" @click="emit('update:visible', false)">
          <X :size="18" :stroke-width="1.75" aria-hidden="true" />
        </button>
      </div>

      <slot />

      <div class="flex justify-end gap-2">
        <slot name="footer" />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import Dialog from 'primevue/dialog'
import { X } from '@lucide/vue'

/**
 * A modal form: an icon tile, title and description over the fields, with the footer's buttons
 * at the bottom right. Submitting the form, by its submit button or Enter in a field, emits submit.
 */
defineProps<{
  visible: boolean
  icon: Component
  title: string
  description?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'submit'): void
}>()

const DIALOG_PT = {
  root: { class: '!w-[min(480px,calc(100vw-2rem))] !rounded-2xl !border-surface-200 !p-0 overflow-hidden dark:!border-surface-800' },
  content: { class: '!p-0' },
  mask: { class: '!bg-surface-950/20 dark:!bg-white/25 backdrop-blur-[3px]' }
}
</script>
