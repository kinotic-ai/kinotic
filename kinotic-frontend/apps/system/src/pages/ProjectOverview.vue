<template>
  <div class="flex flex-col">
    <PageHeader :title="project?.name ?? projectId" :description="project?.description || undefined">
      <template #actions>
        <a v-if="project?.repoFullName" :href="`https://github.com/${project.repoFullName}`" target="_blank" rel="noopener">
          <Button label="Open repository" icon="pi pi-github" severity="secondary" outlined />
        </a>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="flex flex-col gap-4">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard :icon="CloudUpload" :tint="lastRun?.status === ExecutionStatus.FAILED ? TINTS.red : TINTS.sky" label="Last deploy run"
                  :loading="loading" :to="`${basePath}/deployment`" :detail="lastRunDetail">
          <Tag v-if="lastRun" :value="lastRun.status" :severity="executionStatusSeverity(lastRun.status)" />
          <Tag v-else value="Never" severity="secondary" />
        </StatCard>
        <StatCard :icon="Server" :tint="TINTS.orange" label="Services running" :value="runningServices" :loading="loading"
                  :detail="`of ${services.length} microservice workload${services.length === 1 ? '' : 's'}`" :to="`${basePath}/workloads`" />
        <StatCard :icon="LaptopMinimalCheck" :tint="TINTS.purple" label="Deploy runs" :value="deployRuns.length" :loading="loading"
                  :detail="`${failedRuns} failed`" :to="`${basePath}/jobs`" />
        <StatCard :icon="GitBranch" :tint="TINTS.green" label="Repository" :loading="loading"
                  :detail="project?.repoFullName ?? '—'" mono-detail
                  :href="project?.repoFullName ? `https://github.com/${project.repoFullName}` : undefined">
          <Tag v-if="project?.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
          <Tag v-else-if="project?.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
          <Tag v-else value="Connected" severity="success" />
        </StatCard>
      </div>

      <div class="grid gap-4 lg:grid-cols-3">
        <DashboardSection :icon="Server" :tint="TINTS.orange" title="Microservices" :count="services.length"
                          description="The microservice workloads this project's deployments have started."
                          :link-to="`${basePath}/deployment`" link-label="Deployment" class="lg:col-span-2">
          <p v-if="services.length === 0" class="px-5 py-4 text-sm text-muted-color">No microservice workload has been started for this project.</p>
          <ul v-else class="px-3 py-2">
            <li v-for="(service, position) in services" :key="service.id ?? service.name"
                class="flex items-center gap-3 rounded-lg px-2 py-2.5">
              <InitialsTile :name="service.name" :index="position" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-surface-950 dark:text-surface-0" v-tooltip.top="service.name">{{ service.name }}</div>
                <div class="truncate font-mono text-xs text-muted-color">{{ shortImage(service.image) }}</div>
              </div>
              <TimePill :date="service.created" class="hidden md:inline-flex" />
              <Tag :value="service.status" :severity="workloadSeverity(service.status)" />
            </li>
          </ul>
        </DashboardSection>

        <DashboardSection :icon="ProjectsIcon" :tint="TINTS.blue" title="About">
          <div class="px-5 pb-2">
            <FactList :facts="facts" />
          </div>
        </DashboardSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Building2, CalendarClock, CloudUpload, FileCode, GitBranch, Hash, LaptopMinimalCheck, LayoutGrid, Server } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, RepositoryConnectionStatus, WorkloadStatus,
         type JobRun, type Project, type Workload } from '@kinotic-ai/management-api'
import { DashboardSection, DatetimeUtil, FactList, InitialsTile, PageHeader, ProjectsIcon, StatCard, TimePill, TINTS,
         errorMessage, executionStatusSeverity, scanJobRuns, shortSha } from '@kinotic-ai/frontend-common'

import { commitShaOf, isDeployRun } from '@/util/runs'
import { applicationPath, organizationPath, projectPath } from '@/util/scope'
import { scanWorkloads, shortImage, workloadSeverity } from '@/util/workloads'

/**
 * The landing page of one project, from what a platform operator may read: the project record,
 * the state of its deploy runs, and its microservice workloads with how many are running.
 */
const props = defineProps<{
  organizationId: string
  applicationId: string
  projectId: string
}>()

/** How many of the organization's projects the page reads to find this one. */
const PROJECT_PAGE_SIZE = 200

const RepoStatus = RepositoryConnectionStatus

const basePath = computed(() => projectPath(props.organizationId, props.applicationId, props.projectId))

const project = ref<Project | null>(null)
const deployRuns = ref<JobRun[]>([])
const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const lastRun = computed(() => deployRuns.value[0] ?? null)
const failedRuns = computed(() => deployRuns.value.filter(run => run.status === ExecutionStatus.FAILED).length)
const services = computed(() => workloads.value.filter(workload => workload.detached))
const runningServices = computed(() => services.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length)

const lastRunDetail = computed(() => {
  const run = lastRun.value
  let ret: string
  if (run) {
    const sha = commitShaOf(run)
    ret = [sha ? shortSha(sha) : null, run.started ? DatetimeUtil.formatRelativeDate(run.started) : null].filter(Boolean).join(' · ')
  } else {
    ret = 'Pushing to the default branch deploys it'
  }
  return ret
})

const facts = computed(() => {
  const repository = project.value?.repoFullName
  return [
    { label: 'Project id', icon: markRaw(Hash), value: props.projectId, mono: true },
    { label: 'Application', icon: markRaw(LayoutGrid), value: props.applicationId,
      to: applicationPath(props.organizationId, props.applicationId) },
    { label: 'Organization', icon: markRaw(Building2), value: props.organizationId, to: organizationPath(props.organizationId) },
    { label: 'Repository', icon: markRaw(GitBranch), mono: true,
      value: repository ? `${repository}${project.value?.repoDefaultBranch ? ` · ${project.value.repoDefaultBranch}` : ''}` : '—' },
    { label: 'Source of truth', icon: markRaw(FileCode), value: project.value?.sourceOfTruth ?? '—' },
    { label: 'Updated', icon: markRaw(CalendarClock),
      value: project.value?.updated ? DatetimeUtil.formatRelativeDate(project.value.updated) : '—' }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  const scope = { organizationId: props.organizationId, applicationId: props.applicationId, projectId: props.projectId }
  try {
    const [projects, runs, workloadList] = await Promise.all([
      Kinotic.systemOrganizations.findProjects(props.organizationId, Pageable.create(0, PROJECT_PAGE_SIZE)),
      scanJobRuns(scope),
      scanWorkloads(scope)
    ])
    project.value = (projects.content ?? []).find(candidate => candidate.id === props.projectId) ?? null
    deployRuns.value = runs.filter(isDeployRun)
    workloads.value = workloadList
    if (!project.value) {
      error.value = `${props.projectId} is not a project of ${props.applicationId}`
    }
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the project')
  } finally {
    loading.value = false
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId, props.projectId], load, { immediate: true })
</script>
