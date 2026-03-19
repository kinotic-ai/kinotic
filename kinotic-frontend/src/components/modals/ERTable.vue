<template>
  <div class="rounded shadow bg-white text-xs min-w-[200px] border border-gray-300">
    <div>
      <div :class="data.color" class="h-3 w-full"></div>
      <div class="font-bold px-2 py-1 bg-[#f1f2f4] border-b border-gray-300">
        {{ data.label }}
      </div>
    </div>
    <div
      v-for="(field, index) in data.fields"
      :key="index"
      class="relative px-2 py-1 border-t border-gray-300 flex justify-between items-center"
    >
      <span>{{ field }}</span>
      <Handle
        type="source"
        :position="Position.Right"
        :id="`out-${index}`"
        class="w-2 h-2 bg-green-500 absolute right-[-6px] top-1/2 transform -translate-y-1/2"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{ data: { label: string; fields: string[]; color: any } }>()
</script>
