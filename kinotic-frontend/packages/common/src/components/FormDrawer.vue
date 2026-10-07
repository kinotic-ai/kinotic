<template>
  <Drawer
    :visible="visible"
    position="right"
    modal
    blockScroll
    class="!w-full sm:!w-[40rem]"
    :pt="pt"
    @update:visible="onVisibleChange"
  >
    <template #header>
      <div class="min-w-0 pr-4">
        <h2 class="text-lg font-medium text-surface-950 dark:text-surface-0">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm text-surface-600 dark:text-surface-400">{{ description }}</p>
      </div>
    </template>

    <template #closeicon>
      <X :size="20" :stroke-width="1.75" aria-hidden="true" />
    </template>

    <slot />

    <template v-if="$slots.footer" #footer>
      <div class="flex justify-end gap-2">
        <slot name="footer" />
      </div>
    </template>
  </Drawer>
</template>

<script setup lang="ts">
import Drawer from 'primevue/drawer'
import { X } from '@lucide/vue'
import '../styles/modal-mask.css'

/**
 * A panel that slides in from the right over a dimmed, blurred page to create or edit one
 * thing: a title and optional description in the header, the form in the default slot, and
 * the form's buttons in the {@code footer} slot. Emits {@code close} when dismissed by the close
 * button, Escape, or a click on the backdrop.
 */
defineProps<{
  visible: boolean
  title: string
  description?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const pt = {
  mask: { class: 'modal-mask' },
  header: { class: 'items-start border-b border-surface-200 dark:border-surface-800' },
  footer: { class: 'border-t border-surface-200 dark:border-surface-800' },
  // A square hover tile matching the controls' 6px radius, rather than the default circle
  pcCloseButton: {
    root: {
      class: '!h-9 !w-9 !rounded-md !text-surface-700 hover:!bg-surface-100 hover:!text-surface-950 dark:!text-surface-300 dark:hover:!bg-surface-800 dark:hover:!text-surface-0'
    }
  }
}

function onVisibleChange(visible: boolean) {
  if (!visible) {
    emit('close')
  }
}
</script>
