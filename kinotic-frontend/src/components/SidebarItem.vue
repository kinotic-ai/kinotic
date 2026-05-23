<script setup lang="ts">
import Tooltip from 'primevue/tooltip'

const vTooltip = Tooltip

const props = defineProps<{
  icon: string,
  label: string,
  collapsed: boolean,
  textColor?: string,
  path: string,
  isActive: boolean
}>()
</script>

<template>
  <div
    :class="[
      'relative flex w-full cursor-pointer',
      props.collapsed ? 'min-h-[46px] items-center justify-center px-[10px]' : 'min-h-[36px]'
    ]"
    v-tooltip.right="{
      value: props.collapsed ? label : null,
      pt: { text: '!bg-surface-900 !text-surface-0 !text-xs !font-medium', arrow: '!border-r-surface-900' }
    }"
    @click="$emit('click')"
  >
    <span
      v-if="props.isActive && props.collapsed"
      class="app-sidebar-active-rail absolute left-0 top-1/2 h-6 w-[2px] -translate-y-1/2 rounded-r-sm"
    />

    <div
      :class="[
        'flex items-center rounded-lg transition-colors duration-200',
        props.isActive
          ? (props.collapsed ? 'app-sidebar-item--active-collapsed' : 'app-sidebar-item--active-expanded')
          : 'app-sidebar-item--inactive',
        props.collapsed ? 'h-[38px] w-[38px] justify-center' : 'w-full min-h-[36px] px-[10px]'
      ]"
    >
      <div class="min-w-[20px] flex justify-center items-center h-[24px]">
        <i
          :class="['pi', icon, props.collapsed ? '' : 'text-base', props.isActive ? 'app-sidebar-item-icon-active' : 'app-sidebar-item-icon']"
          :style="{ fontSize: '14px', lineHeight: '14px' }"
        />
      </div>
      <div v-if="!props.collapsed" class="w-[8px]"></div>
      <div v-if="!props.collapsed">
        <span
          :class="[ 
            'app-sidebar-item-label inline-block whitespace-nowrap text-sm transform transition-all duration-300 origin-left',
            props.isActive ? 'app-sidebar-item-label-active-expanded' : '',
            props.collapsed ? 'opacity-0 scale-x-0' : 'opacity-100 scale-x-100'
          ]"
        >
          {{ label }}
        </span>
      </div>
    </div>
  </div>
</template>
