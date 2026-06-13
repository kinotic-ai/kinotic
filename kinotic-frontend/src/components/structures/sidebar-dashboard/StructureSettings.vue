<script setup lang="ts">
import { computed } from 'vue'
import {useStructureStore} from "@/stores/editor.ts"
import { isDark as darkMode } from '@/composables/useTheme'

const structureStore = useStructureStore()

const categories = [
  {key: 'TABLE', name: 'Table'},
  {key: 'STREAM', name: 'Stream'}
]
const accessModes = [
  {key: 'NONE', name: 'None'},
  {key: 'SHARED', name: 'Shared'}
]

const entityType = computed({
  get() {
    const entityDecorator = structureStore.structure?.schema?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    return entityDecorator?.entityType || 'TABLE'
  },
  set(value: string) {
    structureStore.updateEntityType(value)
  }
})

const multiTenancyType = computed({
  get() {
    const entityDecorator = structureStore.structure?.schema?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    return entityDecorator?.multiTenancyType || 'NONE'
  },
  set(value: string) {
    structureStore.updateMultiTenancyType(value)
  }
})

const description = computed({
  get() {
    return structureStore.structure?.description || ''
  },
  set(value: string) {
    structureStore.updateStructureDescription(value)
  }
})

const isDark = computed(() => darkMode.value)
</script>

<template>
  <div v-if="structureStore.structure">
    <div :class="['border-b p-6', isDark ? 'border-surface-800' : 'border-surface-200']">
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
              v-model="entityType"
              :inputId="category.key"
              name="category"
              :value="category.key"
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
              v-model="multiTenancyType"
              :inputId="access.key"
              name="access"
              :value="access.key"
          />
          <label :for="access.key" class="text-sm">{{ access.name }}</label>
        </div>
      </div>

      <div>
        <p :class="['text-xs font-medium mb-1', isDark ? 'text-surface-400' : 'text-surface-500']">Description</p>
        <Textarea
            v-model="description"
            autoResize
            size="small"
            rows="5" cols="30"
            class="w-full text-sm"
        />
      </div>
    </div>
  </div>
</template>
