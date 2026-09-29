<template>
  <button
    type="button"
    :class="[
      'app-sidebar-item--inactive flex w-full cursor-pointer items-center gap-3 rounded-lg text-left transition-colors',
      collapsed ? 'h-[46px] justify-center' : 'px-1 py-2'
    ]"
    :aria-label="`Account menu for ${name}`"
    aria-haspopup="true"
    aria-controls="sidebar_user_menu"
    v-tooltip.right="collapsed ? name : null"
    @click="toggleMenu"
  >
    <Avatar :label="initials" shape="circle" class="shrink-0" />
    <div v-if="!collapsed" class="min-w-0">
      <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="name">{{ name }}</div>
      <div class="app-subtle-text truncate text-xs">{{ detail }}</div>
    </div>
  </button>

  <Menu id="sidebar_user_menu" ref="menu" :model="items" :popup="true">
    <template #item="{ item, props: itemProps }">
      <a v-bind="itemProps.action">
        <component :is="item.lucideIcon" :size="18" :stroke-width="1.75" class="p-menu-item-icon" aria-hidden="true" />
        <span class="p-menu-item-label">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Avatar from 'primevue/avatar'
import Menu from 'primevue/menu'
import type { MenuItem } from 'primevue/menuitem'

/**
 * The signed-in user at the foot of the sidebar; opens the account menu. Collapsed, only the
 * avatar shows. Each item renders its {@code lucideIcon} beside its label.
 */
defineProps<{
  collapsed: boolean
  name: string
  /** The line under the name, e.g. the organization or the email. */
  detail: string
  initials: string
  items: MenuItem[]
}>()

const menu = ref<InstanceType<typeof Menu> | null>(null)

function toggleMenu(event: MouseEvent) {
  menu.value?.toggle(event)
}
</script>
