<template>
  <div
    class="relative rounded-xl text-xs min-w-[200px] p-6"
    :style="{
      backgroundColor: colorSet.background,
      border: `1px solid ${colorSet.border}`,
    }"
  >
    <div
      class="absolute top-[-12px] left-3 text-xs font-semibold px-2 py-1 text-white rounded-sm"
      :style="{ backgroundColor: colorSet.label }"
    >
      {{ data.label }}
    </div>
    <div class="flex flex-row overflow-auto gap-4">
      <div
        v-for="(variant, index) in data.variants"
        :key="index"
        class="flex flex-col bg-white rounded shadow min-w-[160px]"
      >
        <div
          class="font-semibold text-surface-900 text-xs p-2 w-full"
          :style="{ backgroundColor: colorSet.header }"
        >
          {{ variant.name || `Variant ${index + 1}` }}
        </div>
        <div
          v-for="(field, fIndex) in variant.fields"
          :key="fIndex"
          class="border-t border-surface-100 text-xs p-2 text-gray-700"
        >
          {{ field }}
        </div>
        <Handle
          type="source"
          :position="Position.Right"
          :id="`out-${index}`"
          class="w-2 h-2 absolute right-[-6px] top-1/2 transform -translate-y-1/2"
          :style="{ backgroundColor: colorSet.label }"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { rowColors } from '@/util/rowColors'

const props = defineProps<{
  data: {
    label: string
    variants: { name?: string; fields: string[] }[]
    rowIndex?: number
  }
}>()

const colorSet = computed(() => {
  const index = props.data.rowIndex ?? 0
  return rowColors[index % rowColors.length]
})
</script>
