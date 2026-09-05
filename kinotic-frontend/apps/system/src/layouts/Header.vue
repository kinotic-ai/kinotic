<template>
  <div class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-800 bg-surface-950 px-4 md:px-6">
    <div class="flex min-w-0 items-center gap-3 text-white">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-surface-300 transition-colors hover:text-surface-0 md:hidden"
        aria-label="Open navigation"
        @click="emit('toggle-nav')"
      >
        <span class="pi pi-bars"></span>
      </button>

      <RouterLink to="/dashboard" class="flex items-center gap-2">
        <img src="@/assets/header-logo.svg" class="h-6 w-[27px]" alt="Kinotic" />
      </RouterLink>

      <!-- On small screens only the deepest segment stays; the sidebar's back row names the rest -->
      <span :class="['text-lg text-surface-600', organizationId ? 'hidden md:inline' : '']">/</span>
      <RouterLink to="/dashboard"
        :class="['items-center gap-1.5 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80', organizationId ? 'hidden md:flex' : 'flex']">
        System
        <span class="text-[11px] font-normal text-surface-500">console</span>
      </RouterLink>

      <template v-if="organizationId">
        <span :class="['text-lg text-surface-600', applicationId ? 'hidden md:inline' : '']">/</span>
        <div :class="applicationId ? 'hidden md:inline-block' : 'inline-block'">
          <HeaderPicker kind="organization" :items="organizationItems" :current="organizationId" @select="selectOrganization" />
        </div>
      </template>

      <template v-if="organizationId && applicationId">
        <span :class="['text-lg text-surface-600', projectId ? 'hidden md:inline' : '']">/</span>
        <div :class="projectId ? 'hidden md:inline-block' : 'inline-block'">
          <HeaderPicker kind="application" :items="applicationItems" :current="applicationId" @select="selectApplication" />
        </div>
      </template>

      <template v-if="organizationId && applicationId && projectId">
        <span class="text-lg text-surface-600">/</span>
        <HeaderPicker kind="project" :items="projectItems" :current="projectId" @select="selectProject" />
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
          <Avatar :label="initials" shape="circle" class="cursor-pointer hover:opacity-80" />
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
import Avatar from 'primevue/avatar'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application, Organization, Project, UserParticipantIdentity } from '@kinotic-ai/management-api'
import { avatarInitials, createDebug, isDark as darkMode, toggleDark } from '@kinotic-ai/frontend-common'

import HeaderPicker, { type PickerItem } from './HeaderPicker.vue'
import { SYSTEM_USER_STATE } from '@/states/SystemUserState'
import { applicationPath, organizationPath, projectPath } from '@/util/scope'

const debug = createDebug('header')

/**
 * The breadcrumb across the top: System / organization / application / project, as deep as the
 * current route goes. Each segment past the first is a switcher; switching keeps the section
 * where the new scope has it and lands on the scope's overview otherwise.
 */
const emit = defineEmits<{
  (e: 'toggle-nav'): void
}>()

/** How many of each the switchers list. */
const PICKER_PAGE_SIZE = 200

const route = useRoute()
const router = useRouter()

const avatarDropdownOpen = ref(false)
const avatarDropdownRef = ref<HTMLElement>()
const profile = ref<UserParticipantIdentity | null>(null)

const organizations = ref<Organization[]>([])
const applications = ref<Application[]>([])
const projects = ref<Project[]>([])

const isDark = darkMode

const organizationId = computed(() => route.params.organizationId as string | undefined)
const applicationId = computed(() => route.params.applicationId as string | undefined)
const projectId = computed(() => route.params.projectId as string | undefined)

const organizationItems = computed<PickerItem[]>(() => organizations.value.map(org => ({ id: org.id ?? '', label: org.name })))
const applicationItems = computed<PickerItem[]>(() => applications.value.map(app => ({ id: app.id, label: app.id })))
const projectItems = computed<PickerItem[]>(() => projects.value.map(project => ({ id: project.id ?? '', label: project.name })))

const initials = computed(() => avatarInitials(profile.value?.displayName, profile.value?.email))

const profileName = computed(() => profile.value?.displayName ?? 'System operator')
const profileDetail = computed(() => profile.value?.email ?? SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? '')

const avatarMenuItemClass = computed(() => [
  'flex w-full items-center px-4 py-2 text-left text-sm',
  isDark.value ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-700 hover:bg-surface-100'
])

// Each list loads once its segment first shows, so the switcher resolves names without a click
watch(organizationId, async id => {
  if (id && organizations.value.length === 0) {
    try {
      const page = await Kinotic.systemOrganizations.findOrganizations(Pageable.create(0, PICKER_PAGE_SIZE))
      organizations.value = page.content ?? []
    } catch (error) {
      debug('Failed to load organizations: %O', error)
    }
  }
}, { immediate: true })

watch(() => [organizationId.value, applicationId.value], async ([orgId, appId]) => {
  applications.value = []
  projects.value = []
  if (!orgId || !appId) {
    return
  }
  try {
    const [apps, orgProjects] = await Promise.all([
      Kinotic.systemOrganizations.findApplications(orgId, Pageable.create(0, PICKER_PAGE_SIZE)),
      Kinotic.systemOrganizations.findProjects(orgId, Pageable.create(0, PICKER_PAGE_SIZE))
    ])
    // the route may have moved on while the requests were in flight
    if (organizationId.value === orgId && applicationId.value === appId) {
      applications.value = apps.content ?? []
      projects.value = (orgProjects.content ?? []).filter(project => project.applicationId === appId)
    }
  } catch (error) {
    debug('Failed to load applications of %s: %O', orgId, error)
  }
}, { immediate: true })

/** The part of the current path below the given scope path, or '' when not inside it. */
function pathBelow(scopePath: string): string {
  return route.path.startsWith(scopePath) ? route.path.slice(scopePath.length) : ''
}

function selectOrganization(id: string) {
  // only the section carries over: an application or an item inside it belongs to this organization alone
  const section = applicationId.value ? '' : pathBelow(organizationPath(organizationId.value ?? '')).split('/')[1]
  router.push(`${organizationPath(id)}${section ? `/${section}` : ''}`)
}

function selectApplication(id: string) {
  // a project page has no counterpart in another application, so those land on the overview
  const section = projectId.value ? '' : pathBelow(applicationPath(organizationId.value ?? '', applicationId.value ?? '')).split('/')[1]
  router.push(`${applicationPath(organizationId.value ?? '', id)}${section ? `/${section}` : ''}`)
}

function selectProject(id: string) {
  const section = pathBelow(projectPath(organizationId.value ?? '', applicationId.value ?? '', projectId.value ?? '')).split('/')[1]
  router.push(`${projectPath(organizationId.value ?? '', applicationId.value ?? '', id)}${section ? `/${section}` : ''}`)
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
}

async function handleLogout() {
  try {
    await SYSTEM_USER_STATE.logout()
  } finally {
    await router.push('/login')
  }
}
</script>
