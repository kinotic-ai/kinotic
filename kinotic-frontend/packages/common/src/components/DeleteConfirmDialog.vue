<template>
  <Dialog
    :visible="visible"
    modal
    :closable="false"
    :showHeader="false"
    :pt="DIALOG_PT"
    @update:visible="close"
  >
    <div class="flex max-h-[min(720px,calc(100vh-2rem))] flex-col">
      <div class="flex items-start gap-4 px-6 pt-6">
        <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-400">
          <TriangleAlert :size="22" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="min-w-0 flex-1 pt-0.5">
          <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">Delete “{{ name }}”?</h2>
          <p class="mt-1 text-sm text-surface-600 dark:text-surface-300">{{ description }}</p>
        </div>
        <button
          type="button"
          class="-mr-1 -mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-950 disabled:opacity-50 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-0"
          aria-label="Close"
          :disabled="deleting"
          @click="close"
        >
          <X :size="18" :stroke-width="1.75" aria-hidden="true" />
        </button>
      </div>

      <!-- only the lists scroll, so the header and the buttons stay in view -->
      <div class="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-6 py-5">
        <div v-if="loading" class="flex flex-col gap-3 rounded-xl bg-surface-50 p-4 dark:bg-surface-800/60" aria-busy="true">
          <Skeleton width="40%" height="0.875rem" />
          <Skeleton width="85%" height="0.875rem" />
          <Skeleton width="70%" height="0.875rem" />
        </div>

        <template v-else>
          <section class="rounded-xl border border-red-100 bg-red-50/70 p-4 dark:border-red-500/25 dark:bg-red-500/10">
            <p class="text-sm font-semibold text-red-700 dark:text-red-400">This will delete:</p>
            <ImpactRows :rows="removes" class="mt-3" />
          </section>

          <section v-if="kept.length > 0" class="rounded-xl border border-surface-200 p-4 dark:border-surface-700">
            <p class="text-sm font-semibold text-surface-800 dark:text-surface-100">This will be kept:</p>
            <ImpactRows :rows="kept" class="mt-3" />
          </section>

          <p v-if="note" class="text-[0.8125rem] text-surface-500 dark:text-surface-400">{{ note }}</p>
          <p class="text-sm font-medium text-surface-800 dark:text-surface-100">This action cannot be undone.</p>
        </template>
      </div>

      <div class="flex justify-end gap-2 px-6 pb-6">
        <Button type="button" severity="secondary" variant="outlined" label="Cancel"
                :disabled="deleting" @click="close" />
        <Button type="button" severity="danger" :label="confirmLabel"
                :loading="deleting" :disabled="loading || deleting" @click="emit('confirm')" />
      </div>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { defineComponent, h, type PropType } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Skeleton from 'primevue/skeleton'
import { ChevronRight, TriangleAlert, X } from '@lucide/vue'
import '../styles/modal-mask.css'
import type { DeleteImpactRow } from '../types/DeleteImpactRow'

/**
 * Confirms a destructive delete by listing everything it removes and everything it keeps, so the
 * user sees the consequences before confirming.
 */
withDefaults(defineProps<{
  visible: boolean
  /** The name of the thing being deleted. */
  name: string
  description: string
  confirmLabel: string
  /** True while the impact is still being looked up. */
  loading?: boolean
  /** True while the delete request is in flight. */
  deleting?: boolean
  removes?: DeleteImpactRow[]
  kept?: DeleteImpactRow[]
  /** A further remark shown under the lists, such as work that finishes after the dialog closes. */
  note?: string
}>(), {
  loading: false,
  deleting: false,
  removes: () => [],
  kept: () => []
})

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'confirm'): void
}>()

const DIALOG_PT = {
  root: { class: '!w-[min(560px,calc(100vw-2rem))] !rounded-2xl !border-surface-200 !p-0 overflow-hidden dark:!border-surface-800' },
  content: { class: '!p-0' },
  mask: { class: 'modal-mask' }
}

function close(): void {
  emit('update:visible', false)
}

// Each row: its icon and label, then the named items as chips after a chevron
const ImpactRows = defineComponent({
  props: { rows: { type: Array as PropType<DeleteImpactRow[]>, required: true } },
  setup(rowProps) {
    return () => h('ul', { class: 'flex flex-col gap-3' }, rowProps.rows.map(row =>
      h('li', { key: row.label, class: 'flex items-start gap-3 text-sm' }, [
        h(row.icon, { size: 18, strokeWidth: 1.75, class: 'mt-px shrink-0 text-surface-700 dark:text-surface-200', 'aria-hidden': 'true' }),
        h('span', { class: 'min-w-0 flex-1 text-surface-900 dark:text-surface-50' }, [
          row.label,
          row.items && row.items.length > 0
            ? h('span', { class: 'mt-1.5 flex flex-wrap items-center gap-1.5' }, [
                h(ChevronRight, { size: 14, strokeWidth: 1.75, class: 'text-surface-400', 'aria-hidden': 'true' }),
                ...row.items.map(item => h('span', {
                  class: 'rounded-md bg-surface-0/80 px-1.5 py-0.5 font-mono text-[0.75rem] text-surface-700 ring-1 ring-surface-200 dark:bg-surface-900/70 dark:text-surface-200 dark:ring-surface-700'
                }, item))
              ])
            : null
        ])
      ])
    ))
  }
})
</script>
