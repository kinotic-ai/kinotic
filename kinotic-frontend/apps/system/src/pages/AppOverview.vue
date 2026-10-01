<template>
  <div class="flex flex-col">
    <PageHeader :title="application?.name ?? applicationId" :description="application?.description || undefined">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="flex flex-col gap-4">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                  :value="stat.value" :detail="stat.detail" :to="stat.to" :loading="loading && stat.value === '—'" />
      </div>

      <div class="grid gap-4 lg:grid-cols-3">
        <DashboardSection :icon="ProjectsIcon" :tint="TINTS.blue" title="Projects" :count="projects.length"
                          description="Each with the state of its last deploy run." :link-to="`${basePath}/projects`"
                          class="lg:col-span-2">
          <!-- The projects' last deploy runs, as one bar and its legend -->
          <div v-if="projects.length > 0" class="px-5 pt-4">
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

          <EmptyChartCharacter v-if="projects.length === 0 && !loading" class="py-6" title="No projects yet" />
          <ul v-else class="px-3 py-2">
            <li v-for="(project, position) in projects" :key="project.id ?? ''">
              <RouterLink :to="projectPath(organizationId, applicationId, project.id ?? '')"
                          class="group flex items-center gap-3 rounded-lg px-2 py-2.5 transition-colors hover:bg-surface-50 dark:hover:bg-surface-800/60">
                <InitialsTile :name="project.name || project.id || ''" :index="position" />
                <div class="min-w-0 flex-1">
                  <div class="truncate text-sm font-medium text-surface-950 dark:text-surface-0">{{ project.name }}</div>
                  <div v-if="project.description" class="truncate text-xs text-muted-color">{{ project.description }}</div>
                </div>
                <Tag v-if="project.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
                <Tag v-else-if="project.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
                <Tag v-else-if="lastRunOf(project)" :value="lastRunOf(project)!.status" :severity="executionStatusSeverity(lastRunOf(project)!.status)" />
                <span v-else class="text-xs text-muted-color">Never deployed</span>
                <ChevronRight :size="16" :stroke-width="1.75" class="shrink-0 text-surface-300 group-hover:text-surface-600 dark:text-surface-600 dark:group-hover:text-surface-300" aria-hidden="true" />
              </RouterLink>
            </li>
          </ul>
        </DashboardSection>

        <DashboardSection :icon="LayoutGrid" :tint="TINTS.purple" title="About"
                          description="As the organization configured it; the settings are theirs to change.">
          <div class="px-5 pb-2">
            <FactList :facts="facts" />
          </div>
        </DashboardSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Boxes, Building2, CalendarClock, ChevronRight, Hash, LaptopMinimalCheck, LayoutGrid, Tag as TagIcon, Users, Waypoints } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, RepositoryConnectionStatus, WorkloadStatus,
         type Application, type JobRun, type Project, type Workload } from '@kinotic-ai/management-api'
import { DashboardSection, DatetimeUtil, FactList, InitialsTile, PageHeader, ProjectsIcon, StatCard, TINTS,
         errorMessage, executionStatusSeverity, scanJobRuns, EmptyChartCharacter } from '@kinotic-ai/frontend-common'

import { deployRunsByProject } from '@/util/runs'
import { applicationPath, organizationPath, projectPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/**
 * The landing page of one application, built from what a platform operator may read: the
 * application record and its projects, its users, its workloads and its runs. Each tile leads
 * to the page with the detail.
 */
const props = defineProps<{
  organizationId: string
  applicationId: string
}>()

/** How many of the organization's applications and projects the page reads to find this one's. */
const PAGE_SIZE = 200

const RepoStatus = RepositoryConnectionStatus

// The health bar's buckets, in the order they stack, keyed by the Tag severity a run status maps to
const HEALTH_BUCKETS = [
  { severity: 'success', label: 'Completed', bar: 'bg-green-500' },
  { severity: 'info', label: 'Running', bar: 'bg-sky-500' },
  { severity: 'warn', label: 'Cancelled', bar: 'bg-amber-500' },
  { severity: 'danger', label: 'Failed', bar: 'bg-red-500' },
  { severity: 'none', label: 'Never deployed', bar: 'bg-surface-300 dark:bg-surface-600' }
]

const basePath = computed(() => applicationPath(props.organizationId, props.applicationId))

const application = ref<Application | null>(null)
const projects = ref<Project[]>([])
const runsByProject = ref<Map<string, JobRun[]>>(new Map())
const runs = ref<JobRun[]>([])
const workloads = ref<Workload[]>([])
const userCount = ref<number | null>(null)
const inviteCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

function lastRunOf(project: Project): JobRun | null {
  return runsByProject.value.get(project.id ?? '')?.[0] ?? null
}

/** How many projects fall in each health bucket by their last deploy run; empty buckets are left out. */
const health = computed(() => {
  const severities = projects.value.map(project => {
    const run = lastRunOf(project)
    return run ? executionStatusSeverity(run.status) : 'none'
  })
  return HEALTH_BUCKETS
      .map(bucket => ({ ...bucket, count: severities.filter(severity => severity === bucket.severity).length }))
      .filter(bucket => bucket.count > 0)
})

const facts = computed(() => {
  const app = application.value
  let tenancy: string
  if (!app) {
    tenancy = '—'
  } else if (app.tenantPerUser) {
    tenancy = 'Tenant per user'
  } else {
    tenancy = 'Shared tenant'
  }
  return [
    { label: 'Name', icon: markRaw(TagIcon), value: app?.name ?? '—' },
    { label: 'Application id', icon: markRaw(Hash), value: props.applicationId, mono: true },
    { label: 'Organization', icon: markRaw(Building2), value: props.organizationId, to: organizationPath(props.organizationId) },
    { label: 'Zone', icon: markRaw(Waypoints), value: `app.${props.organizationId}.${props.applicationId}`, mono: true },
    { label: 'Tenancy', icon: markRaw(Users), value: tenancy },
    { label: 'Updated', icon: markRaw(CalendarClock), value: app?.updated ? DatetimeUtil.formatRelativeDate(app.updated) : '—' }
  ]
})

interface Stat {
  label: string
  value: string
  detail: string
  to: string
  icon: Component
  /** One of TINTS. */
  tint: string
}

const stats = computed<Stat[]>(() => {
  const deployed = projects.value.filter(project => lastRunOf(project) !== null).length
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const failed = runs.value.filter(run => run.status === ExecutionStatus.FAILED).length
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  const pending = inviteCount.value ?? 0
  return [
    {
      label: 'Projects',
      value: `${projects.value.length}`,
      detail: `${deployed} deployed at least once`,
      to: `${basePath.value}/projects`,
      icon: markRaw(ProjectsIcon),
      tint: TINTS.blue
    },
    {
      label: 'Users',
      value: userCount.value?.toString() ?? '—',
      detail: pending === 1 ? '1 pending invite' : `${pending} pending invites`,
      to: `${basePath.value}/users`,
      icon: markRaw(Users),
      tint: TINTS.green
    },
    {
      label: 'Workloads',
      value: `${running}`,
      detail: `running of ${workloads.value.length}`,
      to: `${basePath.value}/workloads`,
      icon: markRaw(Boxes),
      tint: TINTS.sky
    },
    {
      label: 'Jobs',
      value: `${runs.value.length}`,
      detail: `${failed} failed · ${runningRuns} running`,
      to: `${basePath.value}/jobs`,
      icon: markRaw(LaptopMinimalCheck),
      tint: TINTS.purple
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  const orgId = props.organizationId
  const appId = props.applicationId
  const firstPage = Pageable.create(0, 1)
  try {
    const [apps, orgProjects, users, invites, workloadList, runList] = await Promise.all([
      Kinotic.systemOrganizations.findApplications(orgId, Pageable.create(0, PAGE_SIZE)),
      Kinotic.systemOrganizations.findProjects(orgId, Pageable.create(0, PAGE_SIZE)),
      Kinotic.systemOrganizations.findMembers(orgId, appId, firstPage),
      Kinotic.systemOrganizations.findPendingInvites(orgId, appId, firstPage),
      scanWorkloads({ organizationId: orgId, applicationId: appId }),
      scanJobRuns({ organizationId: orgId, applicationId: appId })
    ])
    application.value = (apps.content ?? []).find(app => app.id === appId) ?? null
    projects.value = (orgProjects.content ?? []).filter(project => project.applicationId === appId)
    userCount.value = users.totalElements ?? 0
    inviteCount.value = invites.totalElements ?? 0
    workloads.value = workloadList
    runs.value = runList
    runsByProject.value = deployRunsByProject(runList)
    if (!application.value) {
      error.value = `${appId} is not an application of ${orgId}`
    }
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the application')
  } finally {
    loading.value = false
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], load, { immediate: true })
</script>
