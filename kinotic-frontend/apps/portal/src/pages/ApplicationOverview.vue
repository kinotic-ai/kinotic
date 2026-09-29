<template>
  <div class="flex flex-col">
    <PageHeader :title="applicationId" :description="application?.description">
      <template #actions>
        <Button label="Settings" severity="secondary" outlined
                @click="router.push(`${basePath}/settings`)">
          <template #icon><Settings :size="16" :stroke-width="1.75" aria-hidden="true" /></template>
        </Button>
      </template>
    </PageHeader>

    <div class="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard v-for="tile in tiles" :key="tile.label" :icon="tile.icon" :tint="tile.tint" :label="tile.label"
                :value="tile.value" :loading="tile.value === null" :detail="tile.detail" :to="tile.to" />
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <section :class="[cardClass, 'lg:col-span-2']">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">Projects</h2>
            <span v-if="projectsCount !== null" class="rounded-md bg-surface-100 px-1.5 text-xs font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ projectsCount }}</span>
          </div>
          <RouterLink :to="`${basePath}/projects`" class="flex items-center gap-1 text-xs font-medium text-surface-600 hover:text-surface-950 dark:text-surface-300 dark:hover:text-surface-0">
            View all <ChevronRight :size="14" :stroke-width="1.75" aria-hidden="true" />
          </RouterLink>
        </div>

        <!-- The listed projects' latest deployment phases, as one bar and its legend -->
        <div v-if="!loadingProjects && projects.length > 0" class="mt-4">
          <div class="flex h-2 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" role="img"
               :aria-label="health.map(segment => `${segment.count} ${segment.label.toLowerCase()}`).join(', ')">
            <div v-for="segment in health" :key="segment.label" :class="segment.bar" :style="{ width: `${(segment.count / projects.length) * 100}%` }" />
          </div>
          <div class="mt-2.5 flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted-color">
            <span v-for="segment in health" :key="segment.label" class="flex items-center gap-1.5">
              <span :class="['h-2 w-2 rounded-full', segment.bar]" aria-hidden="true" />
              {{ segment.label }} <span class="font-medium tabular-nums text-surface-800 dark:text-surface-100">{{ segment.count }}</span>
            </span>
          </div>
        </div>

        <div v-if="loadingProjects" class="mt-4 flex flex-col gap-3">
          <Skeleton v-for="n in 3" :key="n" height="2.5rem" />
        </div>
        <p v-else-if="projects.length === 0" class="mt-4 text-sm text-muted-color">
          No projects yet. Create one from the Projects page.
        </p>
        <ul v-else class="-mx-2 mt-3">
          <li v-for="(project, position) in projects" :key="project.id ?? ''">
            <RouterLink :to="`${basePath}/project/${encodeURIComponent(project.id ?? '')}`"
                        class="group flex items-center gap-3 rounded-lg px-2 py-2.5 transition-colors hover:bg-surface-50 dark:hover:bg-surface-800/60">
              <InitialsTile :name="project.name || project.id || ''" :index="position" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ project.name }}</div>
                <div v-if="project.description" class="truncate text-xs text-muted-color">{{ project.description }}</div>
              </div>
              <Tag v-if="project.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
              <Tag v-else-if="project.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
              <Tag v-else-if="deploymentStatus[project.id ?? '']"
                   :value="deploymentStatus[project.id ?? '']"
                   :severity="deploymentStatusSeverity(deploymentStatus[project.id ?? ''])" />
              <span v-else class="text-xs text-muted-color">Not deployed</span>
              <ChevronRight :size="16" :stroke-width="1.75" class="shrink-0 text-surface-300 group-hover:text-surface-600 dark:text-surface-600 dark:group-hover:text-surface-300" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
      </section>

      <section :class="cardClass">
        <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">About</h2>
        <FactList :facts="facts" />
      </section>
    </div>

    <section :class="cardClass" class="mt-4">
      <div class="flex items-center gap-2">
        <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">UIs</h2>
        <span v-if="!loadingProjects" class="rounded-md bg-surface-100 px-1.5 text-xs font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ uis.length }}</span>
      </div>
      <div v-if="loadingProjects" class="mt-4 flex flex-col gap-3">
        <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
      </div>
      <p v-else-if="uis.length === 0" class="mt-4 text-sm text-muted-color">
        No UI has been published yet. A UI a project contains is published with that project's next deployment.
      </p>
      <ul v-else class="mt-3 grid gap-3 md:grid-cols-2">
        <li v-for="ui in uis" :key="ui.id ?? `${ui.projectId}/${ui.name}`" class="flex items-center gap-3 rounded-lg border border-surface-200 px-3 py-2.5 dark:border-surface-700">
          <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.sky]">
            <Globe :size="18" :stroke-width="1.75" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">
              {{ ui.name }}
              <RouterLink :to="`${basePath}/project/${encodeURIComponent(ui.projectId)}`" class="ml-2 text-xs font-normal text-muted-color hover:underline">{{ ui.projectId }}</RouterLink>
            </div>
            <a :href="ui.url" target="_blank" rel="noopener" class="block truncate font-mono text-xs text-sky-600 hover:underline dark:text-sky-300">{{ ui.url }}</a>
          </div>
          <Tag :value="observedPhase(ui.state.observed)" :severity="observedPhaseSeverity(ui.state.observed)" />
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarClock, ChevronRight, Globe, Server, Settings, Table, Tag as TagIcon, Users, Waypoints } from '@lucide/vue'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type Project, DeploymentStatusType, RepositoryConnectionStatus, type UiDeployment } from '@kinotic-ai/management-api'
import { createDebug, DatetimeUtil, deploymentStatusSeverity, FactList, InitialsTile, observedPhase, observedPhaseSeverity, PageHeader, ProjectsIcon, StatCard, TINTS } from '@kinotic-ai/frontend-common'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'

const debug = createDebug('application-overview')

/**
 * The landing page of one application: how much it holds, each count leading to its list,
 * its projects with their deployment state, the facts that identify it, and every UI its
 * projects have published with its site.
 */
const props = defineProps<{
  applicationId: string
}>()

/** How many projects the overview lists before pointing at the Projects page. */
const PROJECT_PREVIEW_COUNT = 5

const router = useRouter()
const RepoStatus = RepositoryConnectionStatus

const cardClass = 'rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-800/30'

// The health bar's buckets, in the order they stack, keyed by the Tag severity a phase maps to
const HEALTH_BUCKETS = [
  { severity: 'success', label: 'Running', bar: 'bg-green-500' },
  { severity: 'info', label: 'In progress', bar: 'bg-sky-500' },
  { severity: 'warn', label: 'Needs attention', bar: 'bg-amber-500' },
  { severity: 'danger', label: 'Failed', bar: 'bg-red-500' },
  { severity: 'none', label: 'Not deployed', bar: 'bg-surface-300 dark:bg-surface-600' }
]

const organizationId = computed(() => USER_STATE.getOrganizationId())
const basePath = computed(() => `/application/${encodeURIComponent(props.applicationId)}`)
const application = computed(() => {
  const current = APPLICATION_STATE.currentApplication
  return current?.id === props.applicationId ? current : null
})

const projects = ref<Project[]>([])
const loadingProjects = ref(true)
const deploymentStatus = ref<Record<string, DeploymentStatusType>>({})
const uis = ref<UiDeployment[]>([])
const usersCount = ref<number | null>(null)
const machinesCount = ref<number | null>(null)

const projectsCount = computed(() =>
    application.value !== null && APPLICATION_STATE.countsLoaded ? APPLICATION_STATE.projectsCount : null)

/** How many listed projects fall in each health bucket; empty buckets are left out. */
const health = computed(() => {
  const severities = projects.value.map(project => {
    const phase = deploymentStatus.value[project.id ?? '']
    return phase ? deploymentStatusSeverity(phase) : 'none'
  })
  return HEALTH_BUCKETS
      .map(bucket => ({ ...bucket, count: severities.filter(severity => severity === bucket.severity).length }))
      .filter(bucket => bucket.count > 0)
})

const facts = computed(() => [
  { label: 'Name', icon: markRaw(TagIcon), value: application.value?.name ?? '—', mono: false },
  { label: 'Zone', icon: markRaw(Waypoints), value: `app.${organizationId.value}.${props.applicationId}`, mono: true },
  { label: 'Tenancy', icon: markRaw(Users), value: application.value?.tenantPerUser ? 'Tenant per user' : 'Shared tenant', mono: false },
  { label: 'Updated', icon: markRaw(CalendarClock),
    value: application.value?.updated ? DatetimeUtil.formatRelativeDate(application.value.updated) : '—', mono: false }
])

const tiles = computed(() => {
  const countsLoaded = application.value !== null && APPLICATION_STATE.countsLoaded
  // Summarises the same phases as the health bar, worst news first
  const count = (label: string) => health.value.find(bucket => bucket.label === label)?.count ?? 0
  let projectsDetail: string
  if (count('Failed') > 0) {
    projectsDetail = `${count('Failed')} failed`
  } else if (count('In progress') > 0) {
    projectsDetail = `${count('In progress')} deploying`
  } else if (count('Needs attention') > 0) {
    projectsDetail = `${count('Needs attention')} need attention`
  } else if (count('Running') > 0) {
    projectsDetail = count('Running') === projects.value.length ? 'all running' : `${count('Running')} running`
  } else {
    projectsDetail = 'none deployed yet'
  }
  return [
    { label: 'Projects', icon: markRaw(ProjectsIcon), tint: TINTS.blue, to: `${basePath.value}/projects`,
      value: countsLoaded ? APPLICATION_STATE.projectsCount : null, detail: projectsDetail },
    { label: 'Entities', icon: markRaw(Table), tint: TINTS.purple, to: `${basePath.value}/entities`,
      value: countsLoaded ? APPLICATION_STATE.entityDefinitionsCount : null, detail: 'across all projects' },
    { label: 'Users', icon: markRaw(Users), tint: TINTS.green, to: `${basePath.value}/users`,
      value: usersCount.value, detail: 'people who sign in to this application' },
    { label: 'Machines', icon: markRaw(Server), tint: TINTS.orange, to: `${basePath.value}/machines`,
      value: machinesCount.value, detail: 'client-credential callers' }
  ]
})

watch(() => props.applicationId, load, { immediate: true })

async function load(): Promise<void> {
  loadingProjects.value = true
  projects.value = []
  deploymentStatus.value = {}
  uis.value = []
  usersCount.value = null
  machinesCount.value = null
  await Promise.all([loadProjects(), loadUsersCount(), loadMachinesCount()])
}

async function loadProjects(): Promise<void> {
  try {
    const page = await Kinotic.projects.findAllForApplication(props.applicationId, Pageable.create(0, PROJECT_PREVIEW_COUNT))
    projects.value = page.content ?? []
    await Promise.all(projects.value.flatMap(project => [loadDeploymentStatus(project), loadUis(project)]))
  } catch (error) {
    debug('Failed to load projects: %O', error)
  } finally {
    loadingProjects.value = false
  }
}

async function loadDeploymentStatus(project: Project): Promise<void> {
  if (!project.id) return
  try {
    const deployment = await Kinotic.projects.findDeployment(project.id)
    if (deployment?.state.observed) {
      deploymentStatus.value[project.id] = deployment.state.observed.phase
    }
  } catch (error) {
    debug('Failed to load deployment for %s: %O', project.id, error)
  }
}

async function loadUis(project: Project): Promise<void> {
  if (!project.id) return
  try {
    const published = await Kinotic.uiDeployments.findAllForProject(project.id)
    uis.value = [...uis.value, ...published].sort((a, b) => a.name.localeCompare(b.name))
  } catch (error) {
    debug('Failed to load UIs for %s: %O', project.id, error)
  }
}

async function loadUsersCount(): Promise<void> {
  try {
    const page = await Kinotic.members.findMembers(props.applicationId, Pageable.create(0, 1))
    usersCount.value = page.totalElements ?? 0
  } catch (error) {
    debug('Failed to count users: %O', error)
  }
}

async function loadMachinesCount(): Promise<void> {
  try {
    const page = await Kinotic.machines.findMachines(props.applicationId, Pageable.create(0, 1))
    machinesCount.value = page.totalElements ?? 0
  } catch (error) {
    debug('Failed to count machines: %O', error)
  }
}
</script>
