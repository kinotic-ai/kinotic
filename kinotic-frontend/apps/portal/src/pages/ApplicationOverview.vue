<template>
  <div class="flex flex-col">
    <PageHeader :title="applicationId" :description="application?.description">
      <template #actions>
        <Button label="Settings" icon="pi pi-cog" severity="secondary" outlined
                @click="router.push(`${basePath}/settings`)" />
      </template>
    </PageHeader>

    <div class="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <RouterLink v-for="tile in tiles" :key="tile.label" :to="tile.to" :class="tileClass">
        <div class="flex items-center gap-2 text-xs text-muted-color">
          <i :class="tile.icon" />
          {{ tile.label }}
        </div>
        <Skeleton v-if="tile.value === null" height="1.75rem" width="3rem" class="mt-2" />
        <div v-else class="mt-2 text-2xl font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ tile.value }}</div>
        <div class="mt-1 text-xs text-muted-color">{{ tile.detail }}</div>
      </RouterLink>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <section :class="cardClass">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">Projects</h2>
          <RouterLink :to="`${basePath}/projects`" class="text-xs font-medium text-primary-500 hover:underline">View all</RouterLink>
        </div>
        <div v-if="loadingProjects" class="mt-4 flex flex-col gap-3">
          <Skeleton v-for="n in 3" :key="n" height="2.25rem" />
        </div>
        <p v-else-if="projects.length === 0" class="mt-4 text-sm text-muted-color">
          No projects yet. Create one from the Projects page.
        </p>
        <ul v-else class="mt-2 divide-y divide-surface-200 dark:divide-surface-700">
          <li v-for="project in projects" :key="project.id ?? ''">
            <RouterLink :to="`${basePath}/project/${encodeURIComponent(project.id ?? '')}`"
                        class="group flex items-center gap-3 py-3">
              <i class="pi pi-folder text-surface-400" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium group-hover:underline">{{ project.name }}</div>
                <div v-if="project.description" class="truncate text-xs text-muted-color">{{ project.description }}</div>
              </div>
              <Tag v-if="project.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
              <Tag v-else-if="project.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
              <Tag v-else-if="deploymentStatus[project.id ?? '']"
                   :value="deploymentStatus[project.id ?? '']"
                   :severity="deploymentStatusSeverity(deploymentStatus[project.id ?? ''])" />
              <span v-else class="text-xs text-muted-color">Not deployed</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section :class="cardClass">
        <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">About</h2>
        <dl class="mt-3 grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-muted-color">Name</dt>
          <dd class="m-0">{{ application?.name ?? '—' }}</dd>
          <dt class="text-muted-color">Zone</dt>
          <dd class="m-0 font-mono">app.{{ organizationId }}.{{ applicationId }}</dd>
          <dt class="text-muted-color">Tenancy</dt>
          <dd class="m-0">{{ application?.tenantPerUser ? 'Tenant per user' : 'Shared tenant' }}</dd>
          <dt class="text-muted-color">Updated</dt>
          <dd class="m-0">{{ application?.updated ? DatetimeUtil.formatRelativeDate(application.updated) : '—' }}</dd>
        </dl>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type Project, DeploymentStatusType, RepositoryConnectionStatus } from '@kinotic-ai/management-api'
import { createDebug, DatetimeUtil, deploymentStatusSeverity, PageHeader } from '@kinotic-ai/frontend-common'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'

const debug = createDebug('application-overview')

/**
 * The landing page of one application: how much it holds, each count leading to its list,
 * its projects with their deployment state, and the facts that identify it.
 */
const props = defineProps<{
  applicationId: string
}>()

/** How many projects the overview lists before pointing at the Projects page. */
const PROJECT_PREVIEW_COUNT = 5

const router = useRouter()
const RepoStatus = RepositoryConnectionStatus

const tileClass = 'rounded-2xl border border-surface-200 bg-surface-0 px-5 py-4 transition-colors hover:bg-surface-50 dark:border-surface-700 dark:bg-surface-800/30 dark:hover:bg-surface-800/60'
const cardClass = 'rounded-2xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30'

const organizationId = computed(() => USER_STATE.getOrganizationId())
const basePath = computed(() => `/application/${encodeURIComponent(props.applicationId)}`)
const application = computed(() => {
  const current = APPLICATION_STATE.currentApplication
  return current?.id === props.applicationId ? current : null
})

const projects = ref<Project[]>([])
const loadingProjects = ref(true)
const deploymentStatus = ref<Record<string, DeploymentStatusType>>({})
const usersCount = ref<number | null>(null)
const machinesCount = ref<number | null>(null)

const tiles = computed(() => {
  const countsLoaded = application.value !== null && APPLICATION_STATE.countsLoaded
  const deploying = Object.values(deploymentStatus.value).filter(s => s === DeploymentStatusType.DEPLOYING).length
  const failed = Object.values(deploymentStatus.value).filter(s => s === DeploymentStatusType.FAILED).length
  let health: string
  if (failed > 0) {
    health = `${failed} failed`
  } else if (deploying > 0) {
    health = `${deploying} deploying`
  } else {
    health = 'deployments healthy'
  }
  return [
    { label: 'Projects', icon: 'pi pi-folder', to: `${basePath.value}/projects`,
      value: countsLoaded ? APPLICATION_STATE.projectsCount : null, detail: health },
    { label: 'Entities', icon: 'pi pi-table', to: `${basePath.value}/entities`,
      value: countsLoaded ? APPLICATION_STATE.entityDefinitionsCount : null, detail: 'across all projects' },
    { label: 'Users', icon: 'pi pi-users', to: `${basePath.value}/users`,
      value: usersCount.value, detail: 'people who sign in to this application' },
    { label: 'Machines', icon: 'pi pi-server', to: `${basePath.value}/machines`,
      value: machinesCount.value, detail: 'client-credential callers' }
  ]
})

watch(() => props.applicationId, load, { immediate: true })

async function load(): Promise<void> {
  loadingProjects.value = true
  projects.value = []
  deploymentStatus.value = {}
  usersCount.value = null
  machinesCount.value = null
  await Promise.all([loadProjects(), loadUsersCount(), loadMachinesCount()])
}

async function loadProjects(): Promise<void> {
  try {
    const page = await Kinotic.projects.findAllForApplication(props.applicationId, Pageable.create(0, PROJECT_PREVIEW_COUNT))
    projects.value = page.content ?? []
    await Promise.all(projects.value.map(loadDeploymentStatus))
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
    if (deployment) {
      deploymentStatus.value[project.id] = deployment.status.type
    }
  } catch (error) {
    debug('Failed to load deployment for %s: %O', project.id, error)
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
