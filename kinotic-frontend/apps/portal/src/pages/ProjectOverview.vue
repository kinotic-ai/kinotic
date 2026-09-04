<template>
  <div class="flex flex-col">
    <PageHeader :title="project?.name ?? projectId" :description="project?.description">
      <template #actions>
        <a v-if="project?.repoFullName" :href="`https://github.com/${project.repoFullName}`" target="_blank" rel="noopener">
          <Button label="Open repository" icon="pi pi-github" severity="secondary" outlined />
        </a>
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="mb-6 grid gap-4 sm:grid-cols-3">
      <RouterLink :to="`${basePath}/deployment`" :class="tileClass">
        <div class="flex items-center gap-2 text-xs text-muted-color"><i class="pi pi-cloud-upload" />Deployment</div>
        <Skeleton v-if="loading" height="1.5rem" width="5rem" class="mt-2" />
        <div v-else class="mt-2">
          <Tag v-if="deployment" :value="deployment.status.type" :severity="deploymentStatusSeverity(deployment.status.type)" />
          <Tag v-else value="Never deployed" severity="secondary" />
        </div>
        <div class="mt-1 text-xs text-muted-color">
          <template v-if="deployment">
            {{ workloadSummary }}
            <span v-if="deployment.updated"> · {{ DatetimeUtil.formatRelativeDate(deployment.updated) }}</span>
          </template>
          <template v-else>Pushing to the default branch deploys it</template>
        </div>
      </RouterLink>

      <RouterLink :to="`${basePath}/entities`" :class="tileClass">
        <div class="flex items-center gap-2 text-xs text-muted-color"><i class="pi pi-table" />Entities</div>
        <Skeleton v-if="entityCount === null" height="1.75rem" width="3rem" class="mt-2" />
        <div v-else class="mt-2 text-2xl font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ entityCount }}</div>
        <div class="mt-1 text-xs text-muted-color">in this project's data model</div>
      </RouterLink>

      <div :class="tileClass">
        <div class="flex items-center gap-2 text-xs text-muted-color"><i class="pi pi-github" />Repository</div>
        <Skeleton v-if="loading" height="1.5rem" width="5rem" class="mt-2" />
        <div v-else class="mt-2">
          <Tag v-if="project?.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
          <Tag v-else-if="project?.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
          <Tag v-else value="Connected" severity="success" />
        </div>
        <div class="mt-1 truncate font-mono text-xs text-muted-color">{{ project?.repoFullName ?? '—' }}</div>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <section :class="cardClass">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">UIs</h2>
          <RouterLink :to="`${basePath}/deployment`" class="text-xs font-medium text-primary-500 hover:underline">Deployment</RouterLink>
        </div>
        <div v-if="loading" class="mt-4 flex flex-col gap-3">
          <Skeleton v-for="n in 2" :key="n" height="2.25rem" />
        </div>
        <p v-else-if="uis.length === 0" class="mt-4 text-sm text-muted-color">
          No UI has been published yet. A UI the project contains is published with its next deployment.
        </p>
        <ul v-else class="mt-2 divide-y divide-surface-200 dark:divide-surface-700">
          <li v-for="ui in uis" :key="ui.id ?? ui.name" class="flex items-center gap-3 py-3">
            <i class="pi pi-globe text-surface-400" />
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm font-medium">{{ ui.name }}</div>
              <a :href="ui.url" target="_blank" rel="noopener" class="block truncate font-mono text-xs text-primary-500 hover:underline">{{ ui.url }}</a>
            </div>
            <Tag :value="ui.status.type" :severity="deploymentStatusSeverity(ui.status.type)" />
          </li>
        </ul>
      </section>

      <section :class="cardClass">
        <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">About</h2>
        <dl class="mt-3 grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-muted-color">Project id</dt>
          <dd class="m-0 font-mono">{{ projectId }}</dd>
          <dt class="text-muted-color">Application</dt>
          <dd class="m-0"><RouterLink :to="`/application/${encodeURIComponent(applicationId)}`" class="hover:underline">{{ applicationId }}</RouterLink></dd>
          <dt class="text-muted-color">Repository</dt>
          <dd class="m-0 font-mono">{{ project?.repoFullName ?? '—' }}<span v-if="project?.repoDefaultBranch"> · {{ project.repoDefaultBranch }}</span></dd>
          <dt class="text-muted-color">Source of truth</dt>
          <dd class="m-0">{{ project?.sourceOfTruth ?? '—' }}</dd>
          <dt class="text-muted-color">Updated</dt>
          <dd class="m-0">{{ project?.updated ? DatetimeUtil.formatRelativeDate(project.updated) : '—' }}</dd>
        </dl>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { Kinotic } from '@kinotic-ai/core'
import { type Project, type ProjectDeployment, RepositoryConnectionStatus, type UiDeployment } from '@kinotic-ai/management-api'
import { DatetimeUtil, deploymentStatusSeverity, PageHeader } from '@kinotic-ai/frontend-common'

/**
 * The landing page of one project: its repository, its deployment state, how many entities
 * it defines, the UIs it has published with their sites, and the facts that identify it. Each
 * tile leads to the page with the detail.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const RepoStatus = RepositoryConnectionStatus

const tileClass = 'rounded-2xl border border-surface-200 bg-surface-0 px-5 py-4 transition-colors hover:bg-surface-50 dark:border-surface-700 dark:bg-surface-800/30 dark:hover:bg-surface-800/60'
const cardClass = 'rounded-2xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30'

const basePath = computed(() => `/application/${encodeURIComponent(props.applicationId)}/project/${encodeURIComponent(props.projectId)}`)

const project = ref<Project | null>(null)
const deployment = ref<ProjectDeployment | null>(null)
const microserviceCount = ref(0)
const uis = ref<UiDeployment[]>([])
const entityCount = ref<number | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const workloadSummary = computed(() => {
  const parts: string[] = []
  if (microserviceCount.value > 0) {
    parts.push(`${microserviceCount.value} microservice${microserviceCount.value === 1 ? '' : 's'}`)
  }
  if (uis.value.length > 0) {
    parts.push(`${uis.value.length} UI${uis.value.length === 1 ? '' : 's'}`)
  }
  return parts.length > 0 ? parts.join(', ') : 'No workloads yet'
})

watch(() => props.projectId, load, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  project.value = null
  deployment.value = null
  microserviceCount.value = 0
  uis.value = []
  entityCount.value = null
  try {
    const [loadedProject, loadedDeployment, microservices, loadedUis, count] = await Promise.all([
      Kinotic.projects.findById(props.projectId),
      Kinotic.projects.findDeployment(props.projectId),
      Kinotic.microserviceDeployments.findAllForProject(props.projectId),
      Kinotic.uiDeployments.findAllForProject(props.projectId),
      Kinotic.entityDefinitions.countForProject(props.projectId)
    ])
    project.value = loadedProject
    deployment.value = loadedDeployment
    microserviceCount.value = microservices.length
    uis.value = loadedUis
    entityCount.value = count
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}
</script>
