<template>
  <Dialog
    :visible="visible"
    modal
    dismissableMask
    :showHeader="false"
    :pt="DIALOG_PT"
    @update:visible="setVisible"
    @show="onShow"
  >
    <div class="flex max-h-[min(640px,80vh)] flex-col" @keydown="onKeydown">
      <div class="flex items-center gap-3 px-6 pt-5">
        <Search :size="18" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
        <input
          ref="inputRef"
          v-model="query"
          type="text"
          placeholder="Search"
          :aria-label="label"
          class="min-w-0 flex-1 border-0 bg-transparent text-base text-surface-950 outline-none placeholder:text-surface-400 dark:text-surface-0"
        />
        <button type="button" class="flex h-9 w-9 items-center justify-center rounded-md text-surface-700 hover:bg-surface-100 dark:text-surface-300 dark:hover:bg-surface-800"
                aria-label="Close search" @click="setVisible(false)">
          <X :size="20" :stroke-width="1.75" aria-hidden="true" />
        </button>
      </div>

      <div class="flex gap-1 border-b border-surface-200 px-6 pb-3 pt-3 dark:border-surface-800" role="tablist">
        <button
          v-for="filter in filters"
          :key="filter.id"
          type="button"
          role="tab"
          :aria-selected="activeFilter === filter.id"
          :class="[
            'rounded-md px-2.5 py-1 text-sm transition-colors',
            activeFilter === filter.id
              ? 'bg-surface-100 text-surface-950 dark:bg-surface-800 dark:text-surface-0'
              : 'text-surface-500 hover:text-surface-950 dark:text-surface-400 dark:hover:text-surface-0'
          ]"
          @click="selectFilter(filter.id)"
        >{{ filter.label }}</button>
      </div>

      <div ref="listRef" class="min-h-0 flex-1 overflow-y-auto px-3 py-2" role="listbox" aria-label="Results">
        <template v-for="section in sections" :key="section.label">
          <div class="px-3 pb-1.5 pt-3 text-xs text-surface-500 dark:text-surface-400">{{ section.label }}</div>
          <button
            v-for="entry in section.entries"
            :key="entry.key"
            type="button"
            role="option"
            :aria-selected="entry.index === activeIndex"
            :data-index="entry.index"
            :class="[
              'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm',
              entry.index === activeIndex ? 'bg-surface-100 text-surface-950 dark:bg-surface-800 dark:text-surface-0' : 'text-surface-800 dark:text-surface-200'
            ]"
            @mousemove="activeIndex = entry.index"
            @click="run(entry)"
          >
            <InitialsTile v-if="entry.tileIndex !== undefined" :name="entry.label" :index="entry.tileIndex" />
            <component :is="entry.icon" v-else :size="18" :stroke-width="1.75" class="shrink-0" aria-hidden="true" />
            <span class="truncate">{{ entry.label }}</span>
            <span v-if="entry.hint" class="truncate font-mono text-xs text-surface-500 dark:text-surface-400">{{ entry.hint }}</span>
            <ArrowUpRight v-if="entry.external" :size="14" :stroke-width="1.75" class="ml-auto shrink-0 text-surface-400" aria-hidden="true" />
            <CornerDownLeft v-else-if="entry.index === activeIndex" :size="14" :stroke-width="1.75" class="ml-auto shrink-0 text-surface-400" aria-hidden="true" />
          </button>
        </template>
        <p v-if="flatEntries.length === 0" class="px-3 py-10 text-center text-sm text-surface-500 dark:text-surface-400">
          Nothing matches “{{ query }}”
        </p>
      </div>

      <div class="flex items-center gap-5 border-t border-surface-200 px-6 py-3 text-xs text-surface-500 dark:border-surface-800 dark:text-surface-400">
        <span class="flex items-center gap-1.5">Close <kbd :class="KBD_CLASS">Esc</kbd></span>
        <span class="flex items-center gap-1.5">Navigate <kbd :class="KBD_CLASS">↑</kbd><kbd :class="KBD_CLASS">↓</kbd></span>
        <span class="flex items-center gap-1.5">Open <kbd :class="KBD_CLASS">↵</kbd></span>
        <span class="flex items-center gap-1.5">Filters <kbd :class="KBD_CLASS">Tab</kbd></span>
      </div>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import { ArrowUpRight, CornerDownLeft, Search, X } from '@lucide/vue'
import '../styles/modal-mask.css'
import type { CommandPaletteEntry } from '../types/CommandPaletteEntry'
import type { CommandPaletteGroup } from '../types/CommandPaletteGroup'
import InitialsTile from './InitialsTile.vue'

/**
 * The ⌘K / Ctrl K search: lists the given groups' entries, filtered by the query and by a tab
 * per group, and driven entirely from the keyboard (arrows, Enter, Tab, Esc). Opens itself on
 * ⌘K / Ctrl K anywhere in the app, and emits show each time it opens so the caller can load
 * what its groups list.
 */
const props = defineProps<{
  visible: boolean
  groups: CommandPaletteGroup[]
  /** The search box's accessible name, e.g. "Search applications, pages and actions". */
  label: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'show'): void
}>()

/** An entry placed in the flat, keyboard-navigable list. */
interface PlacedEntry extends CommandPaletteEntry {
  index: number
}

const ALL = 'all'

const DIALOG_PT = {
  root: { class: '!w-[min(640px,calc(100vw-2rem))] !rounded-2xl !border-surface-200 !p-0 overflow-hidden dark:!border-surface-800' },
  content: { class: '!p-0' },
  mask: { class: 'modal-mask !items-start pt-[12vh]' }
}

const KBD_CLASS = 'rounded border border-surface-300 px-1 py-px font-sans text-[11px] text-surface-600 dark:border-surface-700 dark:text-surface-300'

const query = ref('')
const activeFilter = ref(ALL)
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)

const filters = computed(() => [{ id: ALL, label: 'All' }, ...props.groups.map(group => ({ id: group.label, label: group.label }))])

const sections = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const matches = (entry: CommandPaletteEntry) =>
      !needle || entry.label.toLowerCase().includes(needle) || (entry.hint?.toLowerCase().includes(needle) ?? false)
  let index = 0
  return props.groups
      .filter(group => activeFilter.value === ALL || activeFilter.value === group.label)
      .map(group => ({ label: group.label, entries: group.entries.filter(matches) }))
      .filter(group => group.entries.length > 0)
      .map(group => ({ label: group.label, entries: group.entries.map(entry => ({ ...entry, index: index++ })) }))
})

const flatEntries = computed<PlacedEntry[]>(() => sections.value.flatMap(section => section.entries))

// A new query or filter starts over at the first result
watch([query, activeFilter], () => { activeIndex.value = 0 })

function setVisible(visible: boolean) {
  emit('update:visible', visible)
}

function onShow() {
  query.value = ''
  activeFilter.value = ALL
  activeIndex.value = 0
  emit('show')
  nextTick(() => inputRef.value?.focus())
}

function selectFilter(filter: string) {
  activeFilter.value = filter
  inputRef.value?.focus()
}

function run(entry: CommandPaletteEntry) {
  setVisible(false)
  entry.run()
}

function moveActive(step: number) {
  const count = flatEntries.value.length
  if (count === 0) return
  activeIndex.value = (activeIndex.value + step + count) % count
  nextTick(() => listRef.value?.querySelector(`[data-index="${activeIndex.value}"]`)?.scrollIntoView({ block: 'nearest' }))
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveActive(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const entry = flatEntries.value[activeIndex.value]
    if (entry) run(entry)
  } else if (event.key === 'Tab') {
    // Tab cycles the filters instead of leaving the input
    event.preventDefault()
    const position = filters.value.findIndex(filter => filter.id === activeFilter.value)
    const step = event.shiftKey ? -1 : 1
    activeFilter.value = filters.value[(position + step + filters.value.length) % filters.value.length].id
  }
}

function onGlobalKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    setVisible(!props.visible)
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>
