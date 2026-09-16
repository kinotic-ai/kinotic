<template>
  <div ref="root" class="relative inline-block">
    <button type="button"
            class="flex w-full items-center justify-between gap-2 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80"
            @click="toggle">
      {{ currentLabel }}
      <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
      </svg>
    </button>
    <div v-if="open"
         :class="[
           'absolute top-full left-0 z-50 mt-2 max-h-80 w-64 overflow-y-auto rounded-xl border p-2 shadow-lg',
           isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
         ]">
      <div class="mb-2 w-full">
        <IconField class="w-full">
          <InputIcon class="pi pi-search" />
          <InputText v-model="searchText" :placeholder="`Search ${kind}s`" class="w-full" />
        </IconField>
      </div>
      <div v-for="item in filteredItems" :key="item.id" @click="select(item.id)"
           :class="[
             'flex cursor-pointer items-center justify-between rounded-lg px-4 py-2 text-sm',
             item.id === current
               ? 'bg-primary-50 font-medium text-primary-600'
               : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
           ]">
        <span class="truncate">{{ item.label }}</span>
        <i v-if="item.id === current" class="pi pi-check text-primary-500"></i>
      </div>
      <div v-if="filteredItems.length === 0" class="px-4 py-2 text-sm text-muted-color">No {{ kind }}s match</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'

import { isDark } from '@kinotic-ai/frontend-common'

/** One entry the picker offers. */
export interface PickerItem {
  id: string
  label: string
}

/**
 * A header breadcrumb segment that switches between siblings: the current one's name with a
 * chevron, opening a searchable list. Emits select with the chosen id; {@code opened} fires
 * when the list opens so sibling pickers can close.
 */
const props = defineProps<{
  kind: string
  items: PickerItem[]
  current: string
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
}>()

const root = ref<HTMLElement>()
const open = ref(false)
const searchText = ref('')

const currentLabel = computed(() => props.items.find(item => item.id === props.current)?.label ?? props.current)

const filteredItems = computed(() => {
  const needle = searchText.value.trim().toLowerCase()
  return needle
      ? props.items.filter(item => item.id.toLowerCase().includes(needle) || item.label.toLowerCase().includes(needle))
      : props.items
})

function toggle() {
  open.value = !open.value
}

function select(id: string) {
  open.value = false
  searchText.value = ''
  if (id !== props.current) {
    emit('select', id)
  }
}

function handleClickOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>
