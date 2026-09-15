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
      <div class="grid gap-4 sm:grid-cols-3">
        <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
      </div>

      <section class="rounded-lg border border-surface p-4">
        <h2 class="mb-2 text-base font-semibold">About</h2>
        <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
          <dt class="text-muted-color">Project id</dt>
          <dd class="font-mono">{{ projectId }}</dd>
          <dt class="text-muted-color">Application</dt>
          <dd><RouterLink :to="applicationPath(organizationId, applicationId)" class="hover:underline">{{ applicationId }}</RouterLink></dd>
          <dt class="text-muted-color">Organization</dt>
          <dd><RouterLink :to="organizationPath(organizationId)" class="hover:underline">{{ organizationId }}</RouterLink></dd>
          <dt class="text-muted-color">Repository</dt>
          <dd class="break-all font-mono">{{ project?.repoFullName ?? '—' }}<span v-if="project?.repoDefaultBranch"> · {{ project.repoDefaultBranch }}</span></dd>
          <dt class="text-muted-color">Source of truth</dt>
          <dd>{{ project?.sourceOfTruth ?? '—' }}</dd>
          <dt class="text-muted-color">Updated</dt>
          <dd>{{ project?.updated ? formatEpochDate(project.updated) : '—' }}</dd>
        </dl>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, RepositoryConnectionStatus, WorkloadStatus,
         type JobRun, type Project, type Workload } from '@kinotic-ai/management-api'
import { DatetimeUtil, PageHeader, errorMessage, executionStatusSeverity, scanJobRuns, shortSha } from '@kinotic-ai/frontend-common'

import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
import { commitShaOf, isDeployRun } from '@/util/runs'
import { applicationPath, organizationPath, projectPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

/**
 * The landing page of one project, from what a platform operator may read: the project record,
 * the state of its last deploy run, and how many of its microservice workloads are running.
 */
const props = defineProps<{
  organizationId: string
  applicationId: string
  projectId: string
}>()

/** How many of the organization's projects the page reads to find this one. */
const PROJECT_PAGE_SIZE = 200

const formatEpochDate = DatetimeUtil.formatEpochDate

const basePath = computed(() => projectPath(props.organizationId, props.applicationId, props.projectId))

const project = ref<Project | null>(null)
const lastRun = ref<JobRun | null>(null)
const workloads = ref<Workload[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

interface Stat {
  label: string
  value: string
  description: string
  tag?: string
  to?: string
  icon?: string
  accent?: StatTileAccent
}

const stats = computed<Stat[]>(() => {
  const run = lastRun.value
  const services = workloads.value.filter(workload => workload.detached)
  const running = services.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const repoStatus = project.value?.repoConnectionStatus ?? null
  let repoLabel: string
  let repoSeverity: string
  if (repoStatus === RepositoryConnectionStatus.INITIALIZATION_FAILED) {
    repoLabel = 'Init failed'
    repoSeverity = 'warn'
  } else if (repoStatus === RepositoryConnectionStatus.DISCONNECTED) {
    repoLabel = 'Disconnected'
    repoSeverity = 'danger'
  } else {
    repoLabel = 'Connected'
    repoSeverity = 'success'
  }
  const sha = run ? commitShaOf(run) : null
  return [
    {
      label: 'Last deploy run',
      value: run ? run.status : 'Never',
      description: run
          ? [sha ? shortSha(sha) : null, run.started ? DatetimeUtil.formatEpochDateTime(run.started) : null].filter(Boolean).join(' · ')
          : 'Pushing to the default branch deploys it',
      tag: run ? executionStatusSeverity(run.status) : 'secondary',
      to: `${basePath.value}/deployment`,
      icon: 'pi-cloud-upload',
      accent: run?.status === ExecutionStatus.FAILED ? 'red' : 'green'
    },
    {
      label: 'Services running',
      value: `${running}`,
      description: `of ${services.length} microservice workload${services.length === 1 ? '' : 's'}`,
      to: `${basePath.value}/workloads`,
      icon: 'pi-box',
      accent: 'amber'
    },
    {
      label: 'Repository',
      value: repoLabel,
      description: project.value?.repoFullName ?? '—',
      tag: repoSeverity,
      icon: 'pi-github',
      accent: 'violet'
    }
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
    lastRun.value = runs.find(isDeployRun) ?? null
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
