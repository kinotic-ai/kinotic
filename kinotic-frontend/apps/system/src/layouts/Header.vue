<template>
  <div class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-800 bg-surface-950 px-6">
    <div class="flex items-center gap-3 text-white">
      <RouterLink to="/overview" class="flex items-center gap-2">
        <img src="@/assets/header-logo.svg" class="h-6 w-[27px]" alt="Kinotic" />
      </RouterLink>
      <span class="text-lg text-surface-600">/</span>
      <span class="text-sm font-medium text-surface-300">System Console</span>

      <template v-if="isOrgDetailPage">
        <span class="text-lg text-surface-600">/</span>

        <div ref="orgDropdownRef" class="relative inline-block">
          <button @click="toggleOrgDropdown"
            class="flex w-full items-center justify-between gap-2 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80">
            {{ currentOrgName }}
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          <div v-if="orgDropdownOpen"
            :class="[
              'absolute top-full left-0 z-50 mt-2 max-h-80 w-64 overflow-y-auto rounded-xl border p-2 shadow-lg',
              isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
            ]">
            <div class="w-full mb-2">
              <IconField class="w-full">
                <InputIcon class="pi pi-search" />
                <InputText v-model="searchTextOrg" placeholder="Search organizations" class="w-full" />
              </IconField>
            </div>
            <div v-for="org in filteredOrganizations" :key="org.id ?? ''" @click="selectOrg(org)"
              :class="[
                'flex cursor-pointer items-center justify-between rounded-lg px-4 py-2 text-sm',
                currentOrgId === org.id
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
              ]">
              <span>{{ org.name }}</span>
              <i v-if="currentOrgId === org.id" class="pi pi-check text-primary-500"></i>
            </div>
          </div>
        </div>
      </template>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Organization, UserParticipantIdentity } from '@kinotic-ai/os-api'
import { isDark as darkMode, toggleDark } from '@kinotic-ai/frontend-common'

import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const route = useRoute()
const router = useRouter()

const avatarDropdownOpen = ref(false)
const avatarDropdownRef = ref<HTMLElement>()
const profile = ref<UserParticipantIdentity | null>(null)

const orgDropdownOpen = ref(false)
const orgDropdownRef = ref<HTMLElement>()
const organizations = ref<Organization[]>([])
const searchTextOrg = ref('')

const isDark = darkMode

const isOrgDetailPage = computed(() => typeof route.params.organizationId === 'string')
const currentOrgId = computed(() => route.params.organizationId as string | undefined)

const currentOrgName = computed(() => {
  const current = organizations.value.find(org => org.id === currentOrgId.value)
  return current?.name ?? currentOrgId.value ?? ''
})

const filteredOrganizations = computed(() => {
  const needle = searchTextOrg.value.trim().toLowerCase()
  return needle
      ? organizations.value.filter(org => org.name.toLowerCase().includes(needle) || (org.id ?? '').includes(needle))
      : organizations.value
})

const profileName = computed(() => profile.value?.displayName ?? 'System operator')
const profileDetail = computed(() => profile.value?.email ?? SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? '')

const avatarMenuItemClass = computed(() => [
  'flex w-full items-center px-4 py-2 text-left text-sm',
  isDark.value ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-700 hover:bg-surface-100'
])

// Load once when the org segment first shows, so the switcher and name resolve without a click
watch(isOrgDetailPage, onOrgDetail => {
  if (onOrgDetail && organizations.value.length === 0) {
    loadOrganizations()
  }
}, { immediate: true })

async function loadOrganizations() {
  try {
    const page = await Kinotic.systemOrganizations.findOrganizations(Pageable.create(0, 100))
    organizations.value = page.content ?? []
  } catch {
    // The segment falls back to showing the route's organization id
  }
}

function toggleOrgDropdown() {
  orgDropdownOpen.value = !orgDropdownOpen.value
  if (orgDropdownOpen.value && organizations.value.length === 0) {
    loadOrganizations()
  }
}

function selectOrg(org: Organization) {
  orgDropdownOpen.value = false
  searchTextOrg.value = ''
  if (org.id && org.id !== currentOrgId.value) {
    // Stay on the same section (applications/projects/members) for the newly selected org
    router.push({ name: (route.name as string) ?? 'organization-detail', params: { organizationId: org.id } })
  }
}

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
  if (orgDropdownOpen.value && orgDropdownRef.value && !orgDropdownRef.value.contains(event.target as Node)) {
    orgDropdownOpen.value = false
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
