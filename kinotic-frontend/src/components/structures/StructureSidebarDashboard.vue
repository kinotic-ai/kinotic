<script setup lang="ts">
import { ref } from 'vue'
import RadioButton from 'primevue/radiobutton'
import Textarea from 'primevue/textarea'
import { isDark as darkMode } from '@/composables/useTheme'

const categories = [
  { key: 'table', name: 'Table' },
  { key: 'stream', name: 'Stream' },
]

const accessModes = [
  { key: 'none', name: 'None' },
  { key: 'shared', name: 'Shared' },
]

const selectedCategory = ref<string>('Table')
const selectedAccess = ref<string>('None')
const notes = ref<string>('')

const isDark = darkMode
</script>

<template>
  <div :class="['w-[320px] h-full flex flex-col border', isDark ? 'border-surface-800 bg-surface-900 text-surface-0' : 'border-surface-200 bg-surface-0 text-surface-950']">
    <div :class="['p-6 border-b', isDark ? 'border-surface-800' : 'border-surface-200']">
      <h3 class="text-sm font-semibold">Entity settings</h3>
    </div>
    <div class="flex-1 overflow-y-auto p-6 space-y-6">
      <div class="space-y-3 mb-7">
        <div
          v-for="category in categories"
          :key="category.key"
          class="flex items-center gap-2"
        >
          <RadioButton
            size="small"
            v-model="selectedCategory"
            :inputId="category.key"
            name="category"
            :value="category.name"
          />
          <label :for="category.key" class="text-sm">{{ category.name }}</label>
        </div>
      </div>
      <div class="space-y-3">
        <p :class="['text-xs font-medium', isDark ? 'text-surface-400' : 'text-surface-500']">Multi tenancy</p>
        <div
          v-for="access in accessModes"
          :key="access.key"
          class="flex items-center gap-2"
        >
          <RadioButton
            size="small"
            v-model="selectedAccess"
            :inputId="access.key"
            name="access"
            :value="access.name"
          />
          <label :for="access.key" class="text-sm">{{ access.name }}</label>
        </div>
      </div>
      <div>
        <p :class="['text-xs font-medium mb-1', isDark ? 'text-surface-400' : 'text-surface-500']">Description</p>
        <Textarea
          v-model="notes"
          autoResize
          size="small"
          rows="5" cols="30"
          class="w-full text-sm"
        />
      </div>
    </div>
  </div>
</template>
