<template>
  <div class="flex flex-col">
    <PageHeader :title="application?.name ?? applicationId" :description="application?.description || undefined">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
      </div>

      <div class="grid gap-4 lg:grid-cols-2">
        <section class="rounded-lg border border-surface p-4">
          <div class="mb-2 flex items-start justify-between gap-3">
            <div>
              <h2 class="text-base font-semibold">Projects</h2>
              <p class="text-xs text-muted-color">Each with the state of its last deploy run.</p>
            </div>
            <RouterLink :to="`${basePath}/projects`" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
          </div>
          <p v-if="projects.length === 0 && !loading" class="text-sm text-muted-color">No projects yet.</p>
          <ul v-else class="divide-y divide-surface-200 dark:divide-surface-700">
            <li v-for="project in projects" :key="project.id ?? ''">
              <RouterLink :to="projectPath(organizationId, applicationId, project.id ?? '')"
                          class="group flex items-center gap-3 py-3 text-color no-underline">
                <i class="pi pi-folder text-surface-400" />
                <div class="min-w-0 flex-1">
                  <div class="truncate text-sm font-medium group-hover:underline">{{ project.name }}</div>
                  <div v-if="project.description" class="truncate text-xs text-muted-color">{{ project.description }}</div>
                </div>
                <Tag v-if="project.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
                <Tag v-else-if="project.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
                <Tag v-else-if="lastRunOf(project)" :value="lastRunOf(project)!.status" :severity="executionStatusSeverity(lastRunOf(project)!.status)" />
                <span v-else class="text-xs text-muted-color">Never deployed</span>
              </RouterLink>
            </li>
          </ul>
        </section>

        <section class="rounded-lg border border-surface p-4">
          <h2 class="text-base font-semibold">About</h2>
          <p class="mb-2 text-xs text-muted-color">As the organization configured it; the settings are theirs to change.</p>
          <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
            <dt class="text-muted-color">Name</dt>
            <dd>{{ application?.name ?? '—' }}</dd>
            <dt class="text-muted-color">Application id</dt>
            <dd class="font-mono">{{ applicationId }}</dd>
            <dt class="text-muted-color">Organization</dt>
            <dd><RouterLink :to="organizationPath(organizationId)" class="hover:underline">{{ organizationId }}</RouterLink></dd>
            <dt class="text-muted-color">Zone</dt>
            <dd class="font-mono">app.{{ organizationId }}.{{ applicationId }}</dd>
            <dt class="text-muted-color">Tenancy</dt>
            <dd>{{ application ? (application.tenantPerUser ? 'Tenant per user' : 'Shared tenant') : '—' }}</dd>
            <dt class="text-muted-color">Updated</dt>
            <dd>{{ application?.updated ? formatEpochDate(application.updated) : '—' }}</dd>
          </dl>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, RepositoryConnectionStatus, WorkloadStatus,
         type Application, type JobRun, type Project, type Workload } from '@kinotic-ai/management-api'
import { DatetimeUtil, PageHeader, errorMessage, executionStatusSeverity, scanJobRuns } from '@kinotic-ai/frontend-common'

import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
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
const formatEpochDate = DatetimeUtil.formatEpochDate

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

interface Stat {
  label: string
  value: string
  description: string
  to?: string
  icon?: string
  accent?: StatTileAccent
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
      description: `${deployed} deployed at least once`,
      to: `${basePath.value}/projects`,
      icon: 'pi-folder',
      accent: 'violet'
    },
    {
      label: 'Users',
      value: userCount.value?.toString() ?? '—',
      description: pending === 1 ? '1 pending invite' : `${pending} pending invites`,
      to: `${basePath.value}/users`,
      icon: 'pi-users',
      accent: 'green'
    },
    {
      label: 'Workloads',
      value: `${running}`,
      description: `running of ${workloads.value.length}`,
      to: `${basePath.value}/workloads`,
      icon: 'pi-box',
      accent: 'amber'
    },
    {
      label: 'Jobs',
      value: `${runs.value.length}`,
      description: `${failed} failed · ${runningRuns} running`,
      to: `${basePath.value}/jobs`,
      icon: 'pi-list-check',
      accent: 'sky'
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
