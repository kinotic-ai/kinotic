<template>
  <button
    type="button"
    :class="[
      'app-sidebar-item--inactive flex w-full cursor-pointer items-center gap-3 rounded-lg text-left transition-colors',
      collapsed ? 'h-[46px] justify-center' : 'px-1 py-2'
    ]"
    :aria-label="`Account menu for ${displayName}`"
    aria-haspopup="true"
    aria-controls="sidebar_user_menu"
    v-tooltip.right="collapsed ? displayName : null"
    @click="toggleMenu"
  >
    <Avatar :label="PROFILE_STATE.initials" shape="circle" class="shrink-0" />
    <div v-if="!collapsed" class="min-w-0">
      <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="displayName">
        {{ displayName }}
      </div>
      <div class="app-subtle-text truncate text-xs">{{ organizationId }}</div>
    </div>
  </button>

  <Menu id="sidebar_user_menu" ref="menu" :model="menuItems" :popup="true">
    <template #item="{ item, props: itemProps }">
      <a v-bind="itemProps.action">
        <component :is="item.lucideIcon" :size="18" :stroke-width="1.75" class="p-menu-item-icon" aria-hidden="true" />
        <span class="p-menu-item-label">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref } from 'vue'
import { Link, LogOut, User } from '@lucide/vue'
import { useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Menu from 'primevue/menu'
import type { MenuItem } from 'primevue/menuitem'

import { createDebug } from '@kinotic-ai/frontend-common'
import { PROFILE_STATE } from '@/states/IProfileState'
import { USER_STATE } from '@/states/IUserState'

/**
 * The signed-in user at the foot of the sidebar; opens the account menu (profile, connected
 * apps, logout). Collapsed, only the avatar shows.
 */
defineProps<{
  collapsed: boolean
}>()

const debug = createDebug('sidebar-user-menu')

const router = useRouter()
const menu = ref<InstanceType<typeof Menu> | null>(null)

const organizationId = computed(() => USER_STATE.getOrganizationId())
const displayName = computed(() =>
    PROFILE_STATE.profile?.displayName || PROFILE_STATE.profile?.email || 'Account')

// lucideIcon is rendered by the Menu's item slot
const menuItems: MenuItem[] = [
  { label: 'Profile', lucideIcon: markRaw(User), command: () => router.push('/account/profile') },
  { label: 'Connected apps', lucideIcon: markRaw(Link), command: () => router.push('/account/connected-apps') },
  { separator: true },
  { label: 'Log out', lucideIcon: markRaw(LogOut), command: logout }
]

onMounted(() => {
  PROFILE_STATE.load().catch(error => debug('Failed to load profile: %O', error))
})

function toggleMenu(event: MouseEvent) {
  menu.value?.toggle(event)
}

async function logout() {
  try {
    await USER_STATE.logout()
  } catch (error) {
    debug('Logout failed: %O', error)
  }
  await router.push('/login')
}
</script>
