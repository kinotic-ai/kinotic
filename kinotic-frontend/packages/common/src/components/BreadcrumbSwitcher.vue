<template>
  <div ref="rootRef" class="relative">
    <button
      type="button"
      :class="[
        'flex h-7 items-center justify-center gap-1.5 rounded-md text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-950 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-0',
        placeholder ? 'px-2 text-sm' : 'w-7',
        open ? 'bg-surface-100 text-surface-950 dark:bg-surface-800 dark:text-surface-0' : ''
      ]"
      :aria-label="label"
      aria-haspopup="listbox"
      :aria-expanded="open"
      v-tooltip.right="open || placeholder ? null : label"
      @click="toggle"
    >
      <span v-if="placeholder">{{ placeholder }}</span>
      <ChevronsUpDown :size="16" :stroke-width="1.75" aria-hidden="true" />
    </button>

    <div
      v-if="open"
      class="absolute left-0 top-full z-50 mt-2 w-72 overflow-hidden rounded-xl border border-surface-200 bg-surface-0 shadow-lg dark:border-surface-800 dark:bg-surface-900"
      @keydown="onKeydown"
    >
      <div class="flex items-center gap-2.5 border-b border-surface-200 px-3.5 py-2.5 dark:border-surface-800">
        <Search :size="16" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
        <input
          ref="inputRef"
          v-model="query"
          type="text"
          :placeholder="searchPlaceholder"
          :aria-label="searchPlaceholder"
          class="min-w-0 flex-1 border-0 bg-transparent text-sm text-surface-950 outline-none placeholder:text-surface-400 dark:text-surface-0"
        />
      </div>

      <div class="max-h-64 overflow-y-auto p-1.5" role="listbox" :aria-label="label">
        <button
          v-for="(item, position) in matches"
          :key="item.id"
          type="button"
          role="option"
          :aria-selected="item.id === currentId"
          :class="[
            'flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm text-surface-800 dark:text-surface-100',
            position === activeIndex ? 'bg-surface-100 dark:bg-surface-800' : ''
          ]"
          @mousemove="activeIndex = position"
          @click="choose(item.id)"
        >
          <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
          <Check v-if="item.id === currentId" :size="16" :stroke-width="2" class="shrink-0" aria-hidden="true" />
        </button>
        <p v-if="matches.length === 0" class="px-2.5 py-3 text-sm text-surface-500 dark:text-surface-400">No matches</p>
      </div>

      <div class="border-t border-surface-200 p-1.5 dark:border-surface-800">
        <RouterLink :to="allTo" class="flex rounded-md px-2.5 py-2 text-sm text-surface-800 hover:bg-surface-100 dark:text-surface-100 dark:hover:bg-surface-800" @click="close">
          {{ allLabel }}
        </RouterLink>
      </div>
      <div v-if="createTo" class="border-t border-surface-200 p-1.5 dark:border-surface-800">
        <RouterLink :to="createTo" class="flex items-center gap-2 rounded-md px-2.5 py-2 text-sm text-surface-800 hover:bg-surface-100 dark:text-surface-100 dark:hover:bg-surface-800" @click="close">
          <Plus :size="16" :stroke-width="1.75" aria-hidden="true" />
          {{ createLabel }}
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { Check, ChevronsUpDown, Plus, Search } from '@lucide/vue'

/**
 * The ⌃⌄ button beside a breadcrumb segment: a searchable list of that segment's siblings with the
 * current one ticked, then a link to the full list and, when createTo is set, a link to create a new one. Emits
 * {@code select} with the id picked from the list.
 */
const props = defineProps<{
  items: { id: string, label: string }[]
  currentId?: string
  /** Names the button and the list, e.g. "Switch application". */
  label: string
  /** Text shown in the button when nothing is selected yet, e.g. "Select project". */
  placeholder?: string
  searchPlaceholder: string
  allLabel: string
  allTo: string
  createLabel?: string
  createTo?: string
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
}>()

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const rootRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)

const matches = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return needle
      ? props.items.filter(item => item.label.toLowerCase().includes(needle) || item.id.toLowerCase().includes(needle))
      : props.items
})

watch(query, () => { activeIndex.value = 0 })

onClickOutside(rootRef, close)

function toggle() {
  if (open.value) {
    close()
  } else {
    open.value = true
    query.value = ''
    activeIndex.value = Math.max(0, props.items.findIndex(item => item.id === props.currentId))
    nextTick(() => inputRef.value?.focus())
  }
}

function close() {
  open.value = false
}

function choose(id: string) {
  close()
  emit('select', id)
}

function onKeydown(event: KeyboardEvent) {
  const count = matches.value.length
  if (event.key === 'Escape') {
    close()
  } else if (event.key === 'ArrowDown' && count > 0) {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % count
  } else if (event.key === 'ArrowUp' && count > 0) {
    event.preventDefault()
    activeIndex.value = (activeIndex.value - 1 + count) % count
  } else if (event.key === 'Enter' && matches.value[activeIndex.value]) {
    event.preventDefault()
    choose(matches.value[activeIndex.value].id)
  }
}
</script>
