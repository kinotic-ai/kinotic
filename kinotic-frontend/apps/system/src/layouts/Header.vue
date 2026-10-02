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

      <!-- A segment the route no longer names is one the trail remembers from further down, shown
           muted so the way back stays one click away -->
      <!-- On the platform pages: a way to pick an organization from here -->
      <template v-if="!crumbOrganizationId && organizationItems.length > 0">
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

      <template v-if="crumbOrganizationId">
        <span :class="['text-lg text-surface-300 dark:text-surface-600', crumbApplicationId ? 'hidden md:inline' : '']">/</span>
        <div :class="['items-center gap-1', crumbApplicationId ? 'hidden md:flex' : 'flex']">
          <RouterLink :to="organizationPath(crumbOrganizationId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100"
            :class="{ '!text-surface-400 dark:!text-surface-500': !organizationId }">
            {{ organizationName }}
            <ScopePill kind="organization" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="organizationItems"
            :current-id="crumbOrganizationId"
            label="Switch organization"
            search-placeholder="Find organization…"
            all-label="All organizations"
            all-to="/organizations"
            @select="selectOrganization"
          />
        </div>
      </template>

      <template v-if="crumbOrganizationId && !crumbApplicationId && applicationItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="applicationItems"
          label="Select application"
          placeholder="Select application"
          search-placeholder="Find application…"
          all-label="All applications"
          :all-to="`${organizationPath(crumbOrganizationId)}/applications`"
          @select="selectApplication"
        />
      </template>

      <template v-if="crumbOrganizationId && crumbApplicationId">
        <span :class="['text-lg text-surface-300 dark:text-surface-600', crumbProjectId ? 'hidden md:inline' : '']">/</span>
        <div :class="['items-center gap-1', crumbProjectId ? 'hidden md:flex' : 'flex']">
          <RouterLink :to="applicationPath(crumbOrganizationId, crumbApplicationId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100"
            :class="{ '!text-surface-400 dark:!text-surface-500': !applicationId }">
            {{ crumbApplicationId }}
            <ScopePill kind="application" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="applicationItems"
            :current-id="crumbApplicationId"
            label="Switch application"
            search-placeholder="Find application…"
            all-label="All applications"
            :all-to="`${organizationPath(crumbOrganizationId)}/applications`"
            @select="selectApplication"
          />
        </div>
      </template>

      <template v-if="crumbOrganizationId && crumbApplicationId && !crumbProjectId && projectItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="projectItems"
          label="Select project"
          placeholder="Select project"
          search-placeholder="Find project…"
          all-label="All projects"
          :all-to="`${applicationPath(crumbOrganizationId, crumbApplicationId)}/projects`"
          @select="selectProject"
        />
      </template>

      <template v-if="crumbOrganizationId && crumbApplicationId && crumbProjectId">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <div class="flex items-center gap-1">
          <RouterLink :to="projectPath(crumbOrganizationId, crumbApplicationId, crumbProjectId)"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100"
            :class="{ '!text-surface-400 dark:!text-surface-500': !projectId }">
            {{ projectName }}
            <ScopePill kind="project" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="projectItems"
            :current-id="crumbProjectId"
            label="Switch project"
            search-placeholder="Find project…"
            all-label="All projects"
            :all-to="`${applicationPath(crumbOrganizationId, crumbApplicationId)}/projects`"
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

/** The deepest scopes visited, kept while the route moves back up within them. */
const trail = ref<{ organizationId?: string, applicationId?: string, projectId?: string }>({})

// Going down records the scope; switching to another organization or application starts a new trail
watch([organizationId, applicationId, projectId], ([orgId, appId, projId]) => {
  const next = { ...trail.value }
  if (orgId && orgId !== next.organizationId) {
    next.organizationId = orgId
    next.applicationId = undefined
    next.projectId = undefined
  }
  if (appId && appId !== next.applicationId) {
    next.applicationId = appId
    next.projectId = undefined
  }
  if (projId) {
    next.projectId = projId
  }
  trail.value = next
}, { immediate: true })

const crumbOrganizationId = computed(() => organizationId.value ?? trail.value.organizationId)
const crumbApplicationId = computed(() => applicationId.value
    ?? (crumbOrganizationId.value === trail.value.organizationId ? trail.value.applicationId : undefined))
const crumbProjectId = computed(() => projectId.value
    ?? (crumbApplicationId.value && crumbApplicationId.value === trail.value.applicationId ? trail.value.projectId : undefined))

const organizationItems = computed(() => organizations.value.map(org => ({ id: org.id ?? '', label: org.name })))
const applicationItems = computed(() => applications.value.map(app => ({ id: app.id, label: app.id })))
const projects = computed(() => organizationProjects.value.filter(project => project.applicationId === crumbApplicationId.value))
const projectItems = computed(() => projects.value.map(project => ({ id: project.id ?? '', label: project.name })))

const organizationName = computed(() =>
    organizations.value.find(org => org.id === crumbOrganizationId.value)?.name ?? crumbOrganizationId.value ?? '')
const projectName = computed(() =>
    projects.value.find(project => project.id === crumbProjectId.value)?.name ?? crumbProjectId.value ?? '')

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
watch(crumbOrganizationId, async orgId => {
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
    if (crumbOrganizationId.value === orgId) {
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
  router.push(`${applicationPath(crumbOrganizationId.value ?? '', id)}${section ? `/${section}` : ''}`)
}

function selectProject(id: string) {
  const section = !projectId.value ? '' : pathBelow(projectPath(organizationId.value ?? '', applicationId.value ?? '', projectId.value)).split('/')[1]
  router.push(`${projectPath(crumbOrganizationId.value ?? '', crumbApplicationId.value ?? '', id)}${section ? `/${section}` : ''}`)
}
</script>
