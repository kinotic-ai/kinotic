<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  type: string
  color: string
}>()

const emit = defineEmits<{
  (e: 'edit', event: MouseEvent): void
}>()

function onEdit(event: MouseEvent) {
  emit('edit', event)
}

const parsedTypes = computed(() => {
  const match = props.type?.match(/^(.+)\[\]$/);
  const baseType = match ? match[1] : props.type

  const isSpecialType = (type: string) => ['object', 'enum', 'union'].includes(type)

  const getClass = (type: string) =>
      isSpecialType(type) ? `${props.color}` : 'bg-surface-100'

  if (match) {
    return [
      { label: 'Array', className: 'bg-surface-100' },
      { label: capitalize(baseType), className: getClass(baseType) }
    ]
  }

  return [{ label: capitalize(props.type), className: getClass(props.type) }]
})

function capitalize(str: string) {
  return str ? str.charAt(0).toUpperCase() + str.slice(1) : ''
}
</script>

<template>
  <div class="flex gap-1 justify-end">
    <span
        v-for="(type, index) in parsedTypes"
        :key="index"
        class="text-[10px] px-2 py-1 text-surface-600 rounded-full font-bold whitespace-nowrap cursor-pointer"
        :class="type.className"
        @dblclick.stop="onEdit"
    >
      {{ type.label }}
    </span>
  </div>
</template>
