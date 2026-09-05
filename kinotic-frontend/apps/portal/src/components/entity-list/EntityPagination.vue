<template>
  <div class="border-t border-gray-300 p-3 flex items-center justify-between bg-white flex-shrink-0">
    <div class="text-sm text-gray-600">
      Showing {{ first + 1 }} to {{ Math.min(first + rows, totalItems) }} of {{ totalItems }} results
    </div>
    <div class="flex gap-2">
      <Button
        label="Previous"
        size="small"
        :disabled="first === 0"
        @click="$emit('page', { first: Math.max(0, first - rows), rows: rows })"
      />
      <Button
        label="Next"
        size="small"
        :disabled="first + rows >= totalItems"
        @click="$emit('page', { first: first + rows, rows: rows })"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import Button from 'primevue/button'

defineProps<{
  first: number
  rows: number
  totalItems: number
}>()

defineEmits<{
  (e: 'page', event: { first: number, rows: number }): void
}>()
</script>
