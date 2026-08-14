<template>
  <div class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-800 bg-surface-950 px-6">
    <div class="flex items-center gap-3 text-white">
      <RouterLink to="/overview" class="flex items-center gap-2">
        <img src="@/assets/header-logo.svg" class="h-6 w-[27px]" alt="Kinotic" />
      </RouterLink>
      <span class="text-lg text-surface-600">/</span>
      <span class="text-sm font-medium text-surface-300">System Console</span>
    </div>

    <div class="flex items-center gap-3">
      <button
        type="button"
        class="flex h-9 w-9 items-center justify-center rounded-full border border-surface-800 bg-transparent text-surface-400 transition-colors hover:border-surface-700 hover:text-surface-0"
        :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
        @click="toggleDark()"
      >
        <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
      </button>

      <div ref="avatarDropdownRef" class="relative">
        <button class="flex items-center" @click="avatarDropdownOpen = !avatarDropdownOpen">
          <img src="@/assets/avatar.png" class="h-8 w-8 cursor-pointer rounded-full hover:opacity-80" alt="Account" />
        </button>

        <div v-if="avatarDropdownOpen"
          :class="[
            'absolute top-full right-0 z-50 mt-2 w-56 rounded-xl border shadow-lg',
            isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
          ]">
          <div class="py-1">
            <div class="px-4 py-2">
              <div :class="['text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">
                {{ profileName }}
              </div>
              <div class="text-xs text-muted-color break-all">{{ profileDetail }}</div>
            </div>
            <div :class="['my-1 border-t', isDark ? 'border-surface-800' : 'border-surface-200']"></div>
            <button :class="avatarMenuItemClass" @click="handleLogout">
              <i class="pi pi-sign-out mr-2"></i>
              Logout
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { Kinotic } from '@kinotic-ai/core'
import type { UserParticipantIdentity } from '@kinotic-ai/os-api'
import { isDark as darkMode, toggleDark } from '@kinotic-ai/frontend-common'

import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const router = useRouter()

const avatarDropdownOpen = ref(false)
const avatarDropdownRef = ref<HTMLElement>()
const profile = ref<UserParticipantIdentity | null>(null)

const isDark = darkMode

const profileName = computed(() => profile.value?.displayName ?? 'System operator')
const profileDetail = computed(() => profile.value?.email ?? SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? '')

const avatarMenuItemClass = computed(() => [
  'flex w-full items-center px-4 py-2 text-left text-sm',
  isDark.value ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-700 hover:bg-surface-100'
])

onMounted(async () => {
  document.addEventListener('click', handleClickOutside)
  try {
    profile.value = await Kinotic.profile.findMyProfile()
  } catch {
    // The participant id fallback in profileDetail keeps the menu meaningful
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})

function handleClickOutside(event: MouseEvent) {
  if (avatarDropdownOpen.value && avatarDropdownRef.value && !avatarDropdownRef.value.contains(event.target as Node)) {
    avatarDropdownOpen.value = false
  }
}

async function handleLogout() {
  try {
    await SYSTEM_USER_STATE.logout()
  } finally {
    await router.push('/login')
  }
}
</script>
