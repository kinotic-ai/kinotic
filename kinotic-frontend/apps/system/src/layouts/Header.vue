<template>
  <div class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-200 bg-surface-0 px-4 md:pl-[18px] md:pr-6 dark:border-surface-800 dark:bg-surface-900">
    <div class="relative flex min-w-0 items-center gap-3 text-surface-950 dark:text-surface-0">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-surface-600 transition-colors hover:text-surface-950 md:hidden dark:text-surface-300 dark:hover:text-surface-0"
        aria-label="Open navigation"
        @click="emit('toggle-nav')"
      >
        <span class="pi pi-bars"></span>
      </button>

      <!-- Wide enough that, after the 18px header padding and the 12px gap, the first "/" lands on
           the collapsed sidebar's right edge (73px); it stays there when the sidebar expands -->
      <div class="flex shrink-0 items-center md:w-[43px]">
        <RouterLink to="/dashboard" class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-surface-950 dark:ring-1 dark:ring-surface-700">
          <img src="@/assets/header-logo.svg" class="h-[18px] w-5" alt="Kinotic" />
        </RouterLink>
      </div>

      <!-- On small screens only the deepest segment stays; the sidebar's back row names the rest.
           On wider screens the separator takes no width so the "/" centres on the sidebar edge. -->
      <span :class="['text-lg text-surface-300 md:flex md:w-0 md:justify-center dark:text-surface-600', organizationId ? 'hidden' : '']">/</span>
      <div :class="['items-center gap-1', organizationId ? 'hidden md:flex' : 'flex']">
        <RouterLink to="/dashboard"
          class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
          Kinotic
          <ScopePill kind="system" />
        </RouterLink>
      </div>

      <!-- On the platform pages: a way to pick an organization from here -->
      <template v-if="!organizationId && organizationItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="organizationItems"
          label="Select organization"
          placeholder="Select organization"
          search-placeholder="Find organization…"
          all-label="All organizations"
          all-to="/organizations"
          @select="selectOrganization"
        />
      </template>

      <template v-if="organizationId">
        <span :class="['text-lg text-surface-300 dark:text-surface-600', applicationId ? 'hidden md:inline' : '']">/</span>
        <div :class="['items-center gap-1', applicationId ? 'hidden md:flex' : 'flex']">
          <RouterLink :to="organizationPath(organizationId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
            {{ organizationName }}
            <ScopePill kind="organization" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="organizationItems"
            :current-id="organizationId"
            label="Switch organization"
            search-placeholder="Find organization…"
            all-label="All organizations"
            all-to="/organizations"
            @select="selectOrganization"
          />
        </div>
      </template>

      <template v-if="organizationId && !applicationId && applicationItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="applicationItems"
          label="Select application"
          placeholder="Select application"
          search-placeholder="Find application…"
          all-label="All applications"
          :all-to="`${organizationPath(organizationId)}/applications`"
          @select="selectApplication"
        />
      </template>

      <template v-if="organizationId && applicationId">
        <span :class="['text-lg text-surface-300 dark:text-surface-600', projectId ? 'hidden md:inline' : '']">/</span>
        <div :class="['items-center gap-1', projectId ? 'hidden md:flex' : 'flex']">
          <RouterLink :to="applicationPath(organizationId, applicationId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
            {{ applicationId }}
            <ScopePill kind="application" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="applicationItems"
            :current-id="applicationId"
            label="Switch application"
            search-placeholder="Find application…"
            all-label="All applications"
            :all-to="`${organizationPath(organizationId)}/applications`"
            @select="selectApplication"
          />
        </div>
      </template>

      <template v-if="organizationId && applicationId && !projectId && projectItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="projectItems"
          label="Select project"
          placeholder="Select project"
          search-placeholder="Find project…"
          all-label="All projects"
          :all-to="`${applicationPath(organizationId, applicationId)}/projects`"
          @select="selectProject"
        />
      </template>

      <template v-if="organizationId && applicationId && projectId">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <div class="flex items-center gap-1">
          <RouterLink :to="projectPath(organizationId, applicationId, projectId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
            {{ projectName }}
            <ScopePill kind="project" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="projectItems"
            :current-id="projectId"
            label="Switch project"
            search-placeholder="Find project…"
            all-label="All projects"
            :all-to="`${applicationPath(organizationId, applicationId)}/projects`"
            @select="selectProject"
          />
        </div>
      </template>
    </div>

    <div class="flex items-center gap-2">
      <HeaderSearchButton @open="searchOpen = true" />
      <HeaderIconButton :href="DOCUMENTATION_URL" label="Help (opens the platform documentation in a new tab)" tooltip="Help">
        <CircleHelp :size="18" :stroke-width="1.75" aria-hidden="true" />
      </HeaderIconButton>
      <ThemeToggleButton />
    </div>

    <CommandPalette v-model:visible="searchOpen" :groups="searchGroups" label="Search organizations, pages and actions"
                    @show="loadOrganizations" />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpenText, CircleHelp } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application, Organization, Project } from '@kinotic-ai/management-api'
import { BreadcrumbSwitcher, CommandPalette, type CommandPaletteGroup, createDebug, HeaderIconButton,
         HeaderSearchButton, ScopePill, sidebarPageEntries, ThemeToggleButton } from '@kinotic-ai/frontend-common'

import { applicationPath, organizationPath, projectPath } from '@/util/scope'

const debug = createDebug('header')

/**
 * The breadcrumb across the top: Kinotic / organization / application / project, as deep as the
 * current route goes, each segment labelled with its kind. Each segment past the first is a
 * switcher, and a scope with children but none open offers a picker for them; switching keeps
 * the section where the new scope has it and lands on the scope's overview otherwise.
 */
const emit = defineEmits<{
  (e: 'toggle-nav'): void
}>()

/** How many of each the switchers list. */
const PICKER_PAGE_SIZE = 200

/** The operator documentation, opened from Help and the search dialog. */
const DOCUMENTATION_URL = 'https://kinotic.ai/platform/architecture'

const route = useRoute()
const router = useRouter()

const searchOpen = ref(false)

const organizations = ref<Organization[]>([])
const applications = ref<Application[]>([])
const organizationProjects = ref<Project[]>([])

const organizationId = computed(() => route.params.organizationId as string | undefined)
const applicationId = computed(() => route.params.applicationId as string | undefined)
const projectId = computed(() => route.params.projectId as string | undefined)

const organizationItems = computed(() => organizations.value.map(org => ({ id: org.id ?? '', label: org.name })))
const applicationItems = computed(() => applications.value.map(app => ({ id: app.id, label: app.id })))
const projects = computed(() => organizationProjects.value.filter(project => project.applicationId === applicationId.value))
const projectItems = computed(() => projects.value.map(project => ({ id: project.id ?? '', label: project.name })))

const organizationName = computed(() =>
    organizations.value.find(org => org.id === organizationId.value)?.name ?? organizationId.value ?? '')
const projectName = computed(() =>
    projects.value.find(project => project.id === projectId.value)?.name ?? projectId.value ?? '')

const searchGroups = computed<CommandPaletteGroup[]>(() => [
  {
    label: 'Organizations',
    entries: organizations.value.map((org, position) => ({
      key: `org:${org.id}`,
      label: org.name,
      hint: org.id ?? undefined,
      tileIndex: position,
      run: () => router.push(organizationPath(org.id ?? ''))
    }))
  },
  {
    label: 'Pages',
    entries: sidebarPageEntries(router, { console: 'Console', account: 'Account' }, path => router.push(path))
  },
  {
    label: 'Actions',
    entries: [
      { key: 'action:docs', label: 'Open documentation', icon: markRaw(BookOpenText), external: true,
        run: () => window.open(DOCUMENTATION_URL, '_blank', 'noopener') }
    ]
  }
])

onMounted(loadOrganizations)

async function loadOrganizations(): Promise<void> {
  if (organizations.value.length === 0) {
    try {
      const page = await Kinotic.systemOrganizations.findOrganizations(Pageable.create(0, PICKER_PAGE_SIZE))
      organizations.value = page.content ?? []
    } catch (error) {
      debug('Failed to load organizations: %O', error)
    }
  }
}

// An organization's applications and projects load once it opens, so its pickers are ready without a click
watch(organizationId, async orgId => {
  applications.value = []
  organizationProjects.value = []
  if (!orgId) {
    return
  }
  try {
    const [apps, orgProjects] = await Promise.all([
      Kinotic.systemOrganizations.findApplications(orgId, Pageable.create(0, PICKER_PAGE_SIZE)),
      Kinotic.systemOrganizations.findProjects(orgId, Pageable.create(0, PICKER_PAGE_SIZE))
    ])
    // the route may have moved on while the requests were in flight
    if (organizationId.value === orgId) {
      applications.value = apps.content ?? []
      organizationProjects.value = orgProjects.content ?? []
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
  const section = !organizationId.value || applicationId.value ? '' : pathBelow(organizationPath(organizationId.value)).split('/')[1]
  router.push(`${organizationPath(id)}${section ? `/${section}` : ''}`)
}

function selectApplication(id: string) {
  // a project page has no counterpart in another application, so those land on the overview
  const section = !applicationId.value || projectId.value ? '' : pathBelow(applicationPath(organizationId.value ?? '', applicationId.value)).split('/')[1]
  router.push(`${applicationPath(organizationId.value ?? '', id)}${section ? `/${section}` : ''}`)
}

function selectProject(id: string) {
  const section = !projectId.value ? '' : pathBelow(projectPath(organizationId.value ?? '', applicationId.value ?? '', projectId.value)).split('/')[1]
  router.push(`${projectPath(organizationId.value ?? '', applicationId.value ?? '', id)}${section ? `/${section}` : ''}`)
}
</script>
