<template>
  <div
    class="rounded-lg overflow-hidden bg-white text-xs min-w-[200px] border border-surface-100"
  >
    <div
      class="p-3 font-semibold text-surface-900 flex items-center gap-2"
      :style="{ backgroundColor: colorSet.header }"
    >
      {{ data.label }}
    </div>
    <div
      v-for="(field, index) in data.fields"
      :key="index"
      class="relative px-2 py-1 border-t border-surface-100 flex justify-between items-center"
    >
      <span class="font-normal text-xs text-surface-900">{{ field.name }}</span>
      <Handle
        type="source"
        :position="Position.Right"
        :id="`out-${index}`"
        class="w-2 h-2 opacity-0 absolute right-[-6px] top-1/2 transform -translate-y-1/2"
      />
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
    fields: { name: string; type: string }[]
    rowIndex?: number
  }
}>()

const colorSet = computed(() => {
  const index = props.data.rowIndex ?? 0
  return rowColors[index % rowColors.length]
})
</script>
