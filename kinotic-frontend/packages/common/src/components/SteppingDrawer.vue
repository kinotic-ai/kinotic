<template>
  <Drawer :visible="visible" position="right" modal blockScroll class="!w-full lg:!w-[min(72rem,92vw)]" :pt="DRAWER_PT"
          @update:visible="emit('update:visible', $event)" @hide="emit('hide')">
    <template #header>
      <div class="flex min-w-0 flex-1 items-center gap-3 pr-3">
        <span class="shrink-0 text-sm tabular-nums text-surface-600 dark:text-surface-300">{{ position }} of {{ total }}</span>
        <span class="flex shrink-0 gap-1">
          <button v-for="step in STEPS" :key="step.delta" type="button" :class="BUTTON_CLASS" :aria-label="step.label"
                  v-tooltip.bottom="step.label" :disabled="!canStep(step.delta)" @click="emit('step', step.delta)">
            <component :is="step.icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
          </button>
        </span>
        <span class="ml-2 flex min-w-0 items-center gap-2 border-l border-surface-200 pl-4 dark:border-surface-700">
          <slot name="title" />
        </span>
        <RouterLink v-if="expandTo" :to="expandTo" :class="[BUTTON_CLASS, 'ml-auto']" :aria-label="expandLabel" v-tooltip.bottom="expandLabel">
          <Maximize2 :size="15" :stroke-width="1.75" aria-hidden="true" />
        </RouterLink>
      </div>
    </template>
    <template #closeicon>
      <X :size="20" :stroke-width="1.75" aria-hidden="true" />
    </template>
    <slot />
  </Drawer>
</template>

<script lang="ts">
/** The open drawers, in the order they opened. */
const OPEN_DRAWERS: symbol[] = []
</script>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Drawer from 'primevue/drawer'
import { ChevronDown, ChevronUp, Maximize2, X } from '@lucide/vue'
import '../styles/modal-mask.css'

/**
 * A drawer showing one item of a list beside it: the item's place in the list, arrows and the
 * arrow keys to step to the previous or next item (emitted as step with -1 or 1), the item's
 * title, and, given {@code expandTo}, a link to the item's own page.
 */
const props = withDefaults(defineProps<{
  visible: boolean
  /** The shown item's place in the list, from 1. */
  position: number
  total: number
  expandTo?: RouteLocationRaw
  expandLabel?: string
}>(), {
  expandLabel: 'Open the full page'
})

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'step', delta: number): void
  (e: 'hide'): void
}>()

const BUTTON_CLASS = 'flex h-8 w-8 items-center justify-center rounded-md border border-surface-200 text-surface-600 transition-colors hover:bg-surface-100 hover:text-surface-950 disabled:pointer-events-none disabled:opacity-40 dark:border-surface-700 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-0'
const STEPS = [
  { delta: -1, label: 'Previous', icon: ChevronUp },
  { delta: 1, label: 'Next', icon: ChevronDown }
]
const DRAWER_PT = {
  mask: { class: 'modal-mask' },
  header: { class: 'border-b border-surface-200 dark:border-surface-800' },
  pcCloseButton: {
    root: {
      class: '!h-9 !w-9 !rounded-md !text-surface-700 hover:!bg-surface-100 hover:!text-surface-950 dark:!text-surface-300 dark:hover:!bg-surface-800 dark:hover:!text-surface-0'
    }
  }
}

// Drawers open over one another (a node's drawer holds a workloads table with its own); the arrow
// keys belong to the one on top, the last opened
const drawerToken = Symbol('stepping-drawer')

function canStep(delta: number): boolean {
  const next = props.position + delta
  return props.position > 0 && next >= 1 && next <= props.total
}

// The arrow keys step while the drawer is open, unless a field inside it has the focus
function onKeydown(event: KeyboardEvent) {
  const target = event.target
  const typing = target instanceof Element && target.closest('input, textarea, select, [contenteditable="true"]')
  const onTop = OPEN_DRAWERS[OPEN_DRAWERS.length - 1] === drawerToken
  if (onTop && !typing && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
    const delta = event.key === 'ArrowUp' ? -1 : 1
    event.preventDefault()
    if (canStep(delta)) {
      emit('step', delta)
    }
  }
}

function release() {
  window.removeEventListener('keydown', onKeydown)
  const index = OPEN_DRAWERS.indexOf(drawerToken)
  if (index >= 0) {
    OPEN_DRAWERS.splice(index, 1)
  }
}

watch(() => props.visible, visible => {
  if (visible) {
    OPEN_DRAWERS.push(drawerToken)
    window.addEventListener('keydown', onKeydown)
  } else {
    release()
  }
}, { immediate: true })

onBeforeUnmount(release)
</script>
