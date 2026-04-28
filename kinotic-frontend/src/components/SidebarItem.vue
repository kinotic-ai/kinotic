<script setup lang="ts">
import { computed } from 'vue'
import { isDark as darkMode } from '@/composables/useTheme'

const props = defineProps<{
  icon: string,
  label: string,
  collapsed: boolean,
  textColor?: string,
  path: string,
  isActive: boolean
}>()

const isDark = computed(() => darkMode.value)

const textClass = computed(() => {
  if (props.isActive) return isDark.value ? 'text-surface-0' : 'text-surface-950'
  return isDark.value ? 'text-surface-400' : 'text-surface-500'
})

const iconClass = computed(() => {
  return props.isActive ? 'text-primary-500' : isDark.value ? 'text-surface-500' : 'text-surface-400'
})
</script>

<template>
  <div
    :class="[
      'flex w-full items-center rounded-md pl-1 pb-2 transition-colors duration-200 cursor-pointer',
      props.isActive ? '' : 'bg-transparent',
      isDark ? 'hover:bg-surface-800' : 'hover:bg-surface-200',
      props.collapsed ? 'justify-center' : 'px-2 '
    ]"
    @click="$emit('click')"
  >
    <div class="min-w-[20px] flex justify-center items-center h-[24px]">
      <i
        :class="['pi', icon, props.collapsed ? '' : 'text-base', iconClass]"
        :style="{ fontSize: '14px', lineHeight: '14px' }"
      />
    </div>
    <div class="w-[8px]"></div>
    <div v-if="!props.collapsed">
      <span
        :class="[ 
          'inline-block whitespace-nowrap text-sm transform transition-all duration-300 origin-left',
          textClass, 
          props.isActive ? 'font-medium' : 'font-normal',
          props.collapsed ? 'opacity-0 scale-x-0' : 'opacity-100 scale-x-100'
        ]"
      >
        {{ label }}
      </span>
    </div>
  </div>
</template>
