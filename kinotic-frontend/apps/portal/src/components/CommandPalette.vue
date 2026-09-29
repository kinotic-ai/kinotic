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
          aria-label="Search applications, pages and actions"
          class="min-w-0 flex-1 border-0 bg-transparent text-base text-surface-950 outline-none placeholder:text-surface-400 dark:text-surface-0"
        />
        <button type="button" class="flex h-9 w-9 items-center justify-center rounded-md text-surface-700 hover:bg-surface-100 dark:text-surface-300 dark:hover:bg-surface-800"
                aria-label="Close search" @click="setVisible(false)">
          <X :size="20" :stroke-width="1.75" aria-hidden="true" />
        </button>
      </div>

      <div class="flex gap-1 border-b border-surface-200 px-6 pb-3 pt-3 dark:border-surface-800" role="tablist">
        <button
          v-for="filter in FILTERS"
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
            <InitialsTile v-if="entry.kind === 'application'" :name="entry.label" :index="entry.tileIndex ?? 0" />
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
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import { ArrowUpRight, BookOpenText, CornerDownLeft, Plus, Search, X } from '@lucide/vue'
import type { SidebarItemMeta } from '@kinotic-ai/frontend-common'
import InitialsTile from '@/components/InitialsTile.vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { DOCUMENTATION_URL } from '@/util/externalLinks'

/**
 * The ⌘K / Ctrl K search: finds applications, the organization and account pages, and common
 * actions, filtered by a tab row and driven entirely from the keyboard (arrows, Enter, Tab, Esc).
 * Also opens itself on ⌘K / Ctrl K anywhere in the portal.
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
}>()

type FilterId = 'all' | 'applications' | 'pages' | 'actions'
type EntryKind = 'application' | 'page' | 'action'

interface PaletteEntry {
  key: string
  kind: EntryKind
  label: string
  /** Secondary text beside the label, e.g. an application's id. */
  hint?: string
  icon?: Component
  /** Colour position of an application's tile. */
  tileIndex?: number
  /** Opens in a new tab rather than navigating inside the portal. */
  external?: boolean
  run: () => void
  /** Position in the flat, keyboard-navigable list. */
  index: number
}

const FILTERS: { id: FilterId, label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'applications', label: 'Applications' },
  { id: 'pages', label: 'Pages' },
  { id: 'actions', label: 'Actions' }
]

// Sidebar groups whose pages need no route params, so they can be opened from anywhere; listed in this order
const PAGE_GROUPS: Record<string, string> = { organization: 'Organization', account: 'Account' }
const PAGE_GROUP_ORDER = Object.keys(PAGE_GROUPS)

const DIALOG_PT = {
  root: { class: '!w-[min(640px,calc(100vw-2rem))] !rounded-2xl !border-surface-200 !p-0 overflow-hidden dark:!border-surface-800' },
  content: { class: '!p-0' },
  mask: { class: '!bg-surface-950/20 backdrop-blur-[3px] !items-start pt-[12vh]' }
}

const KBD_CLASS = 'rounded border border-surface-300 px-1 py-px font-sans text-[11px] text-surface-600 dark:border-surface-700 dark:text-surface-300'

const router = useRouter()

const query = ref('')
const activeFilter = ref<FilterId>('all')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)

function go(path: string) {
  setVisible(false)
  router.push(path)
}

const applicationEntries = computed(() =>
    APPLICATION_STATE.allApplications.map((app, position) => ({
      key: `app:${app.id}`,
      kind: 'application' as const,
      label: app.name || app.id,
      hint: app.id,
      tileIndex: position,
      run: () => go(`/application/${encodeURIComponent(app.id)}`)
    })))

const pageEntries = computed(() =>
    router.getRoutes()
          .filter(record => {
            const sidebar = record.meta?.sidebar as SidebarItemMeta | undefined
            return sidebar && PAGE_GROUPS[sidebar.group] && !record.path.includes(':')
          })
          .sort((a, b) => {
            const left = a.meta.sidebar as SidebarItemMeta
            const right = b.meta.sidebar as SidebarItemMeta
            return PAGE_GROUP_ORDER.indexOf(left.group) - PAGE_GROUP_ORDER.indexOf(right.group) || left.order - right.order
          })
          .map(record => {
            const sidebar = record.meta.sidebar as SidebarItemMeta
            return {
              key: `page:${record.path}`,
              kind: 'page' as const,
              label: sidebar.label,
              hint: PAGE_GROUPS[sidebar.group],
              icon: markRaw(sidebar.icon),
              run: () => go(record.path)
            }
          }))

const actionEntries = [
  { key: 'action:new-application', kind: 'action' as const, label: 'New application', icon: markRaw(Plus),
    run: () => go('/applications?add=true') },
  { key: 'action:docs', kind: 'action' as const, label: 'Open documentation', icon: markRaw(BookOpenText), external: true,
    run: () => { setVisible(false); window.open(DOCUMENTATION_URL, '_blank', 'noopener') } }
]

const sections = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const matches = (entry: { label: string, hint?: string }) =>
      !needle || entry.label.toLowerCase().includes(needle) || (entry.hint?.toLowerCase().includes(needle) ?? false)
  const groups: { label: string, filter: FilterId, entries: Omit<PaletteEntry, 'index'>[] }[] = [
    { label: 'Applications', filter: 'applications', entries: applicationEntries.value.filter(matches) },
    { label: 'Pages', filter: 'pages', entries: pageEntries.value.filter(matches) },
    { label: 'Actions', filter: 'actions', entries: actionEntries.filter(matches) }
  ]
  let index = 0
  return groups
      .filter(group => (activeFilter.value === 'all' || activeFilter.value === group.filter) && group.entries.length > 0)
      .map(group => ({ label: group.label, entries: group.entries.map(entry => ({ ...entry, index: index++ })) }))
})

const flatEntries = computed<PaletteEntry[]>(() => sections.value.flatMap(section => section.entries))

// A new query or filter starts over at the first result
watch([query, activeFilter], () => { activeIndex.value = 0 })

function setVisible(visible: boolean) {
  emit('update:visible', visible)
}

function onShow() {
  query.value = ''
  activeFilter.value = 'all'
  activeIndex.value = 0
  if (APPLICATION_STATE.allApplications.length === 0) {
    void APPLICATION_STATE.loadAllApplications()
  }
  nextTick(() => inputRef.value?.focus())
}

function selectFilter(filter: FilterId) {
  activeFilter.value = filter
  inputRef.value?.focus()
}

function run(entry: PaletteEntry) {
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
    const position = FILTERS.findIndex(filter => filter.id === activeFilter.value)
    const step = event.shiftKey ? -1 : 1
    activeFilter.value = FILTERS[(position + step + FILTERS.length) % FILTERS.length].id
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
