<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  icon: string,
  label: string,
  collapsed: boolean,
  textColor?: string,
  path: string,
  isActive: boolean
}>()

const textClass = computed(() => {
  return props.isActive ? 'text-surface-800' : 'text-surface-500';
});

const iconClass = computed(() => {
  return props.isActive ? 'text-primary-500' : 'text-surface-500';
});
</script>

<template>
  <div
    :class="[
      'flex items-center cursor-pointer w-max pb-2 rounded-md hover:bg-surface-200 pl-1 transition-colors duration-200',
      props.isActive ? '' : 'bg-transparent', 
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
          'inline-block whitespace-nowrap text-sm font-normal transform transition-all duration-300 origin-left', 
          textClass, 
          props.collapsed ? 'opacity-0 scale-x-0' : 'opacity-100 scale-x-100'
        ]"
      >
        {{ label }}
      </span>
    </div>
  </div>
</template>
