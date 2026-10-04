<template>
  <div class="flex flex-col">
    <PageHeader :title="project?.name ?? projectId" :description="project?.description">
      <template #actions>
        <a v-if="project?.repoFullName" :href="`https://github.com/${project.repoFullName}`" target="_blank" rel="noopener">
          <Button label="Open repository" icon="pi pi-github" severity="secondary" outlined />
        </a>
      </template>
    </PageHeader>

    <NoAccessState v-if="error && isAuthorizationError(error)" :back-to="`/application/${encodeURIComponent(applicationId)}/projects`" back-label="Back to projects" />
    <Message v-else-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard :tint="HEARTBEAT_TINTS[heartbeat]" label="Deployment" :loading="loading" :to="`${basePath}/deployment`"
                :detail="deployment ? deploymentDetail : 'Pushing to the default branch deploys it'">
        <template #icon><HeartbeatIcon :state="heartbeat" :size="20" :stroke-width="1.75" /></template>
        <Tag v-if="deployment" :value="deployment.state.observed?.phase ?? 'UNKNOWN'"
             :severity="observedPhaseSeverity(deployment.state.observed)" />
        <Tag v-else value="Never deployed" severity="secondary" />
      </StatCard>
      <StatCard :icon="Server" :tint="TINTS.purple" label="Microservices" :value="microserviceCount" :loading="loading"
                detail="deployed from this project" :to="`${basePath}/deployment`" />
      <StatCard :icon="Table" :tint="TINTS.purple" label="Entities" :value="entityCount ?? '—'" :loading="loadingEntities"
                detail="in this project's data model" :to="`${basePath}/entities`" />
      <StatCard :icon="GitBranch" :tint="TINTS.purple" label="Repository" :loading="loading"
                :detail="project?.repoFullName ?? '—'" mono-detail
                :href="project?.repoFullName ? `https://github.com/${project.repoFullName}` : undefined">
        <Tag v-if="project?.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED" value="Init failed" severity="warn" />
        <Tag v-else-if="project?.repoConnectionStatus === RepoStatus.DISCONNECTED" value="Disconnected" severity="danger" />
        <Tag v-else value="Connected" severity="success" />
      </StatCard>
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <section :class="[cardClass, 'flex flex-col lg:col-span-2']">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.purple]">
              <Boxes :size="18" :stroke-width="1.75" aria-hidden="true" />
            </span>
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">What it runs</h2>
                <span v-if="!loading" class="rounded-md bg-surface-100 px-1.5 text-xs font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ microservices.length + uis.length }}</span>
              </div>
              <p class="text-xs text-muted-color">The UIs and microservices its deployment serves.</p>
            </div>
          </div>
          <RouterLink :to="`${basePath}/deployment`" class="flex items-center gap-1 text-xs font-medium text-surface-600 hover:text-surface-950 dark:text-surface-300 dark:hover:text-surface-0">
            Deployment <ChevronRight :size="14" :stroke-width="1.75" aria-hidden="true" />
          </RouterLink>
        </div>
        <div v-if="loading" class="mt-4 grid gap-3 md:grid-cols-2">
          <Skeleton v-for="n in 2" :key="n" height="7rem" />
        </div>
        <EmptyChartCharacter v-else-if="uis.length === 0 && microservices.length === 0" class="py-6" title="Nothing is running yet"
                             hint="The UIs and microservices the project contains start with its next deployment." />
        <!-- the cards grow to fill the section, so it stays as tall as the About card beside it -->
        <ul v-else class="mt-4 grid flex-1 auto-rows-fr gap-3 md:grid-cols-2">
          <!-- a UI as the website it is: a small browser window showing its address -->
          <li v-for="ui in uis" :key="`ui-${ui.id ?? ui.name}`"
              class="flex flex-col overflow-hidden rounded-xl border border-surface-200 dark:border-surface-700">
            <div class="flex items-center gap-2 border-b border-surface-200 bg-surface-50 px-3 py-2 dark:border-surface-700 dark:bg-surface-800/60">
              <span class="flex shrink-0 gap-1" aria-hidden="true">
                <span class="h-2 w-2 rounded-full bg-surface-300 dark:bg-surface-600" />
                <span class="h-2 w-2 rounded-full bg-surface-300 dark:bg-surface-600" />
                <span class="h-2 w-2 rounded-full bg-surface-300 dark:bg-surface-600" />
              </span>
              <span class="flex min-w-0 flex-1 items-center gap-1.5 rounded-md bg-surface-0 px-2 py-0.5 dark:bg-surface-900">
                <Globe :size="11" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
                <span class="truncate font-mono text-[0.6875rem] text-surface-600 dark:text-surface-300" v-tooltip.top="ui.url">{{ ui.url }}</span>
              </span>
            </div>
            <div class="flex flex-1 items-center gap-3 px-4 py-4">
              <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.purple]">
                <AppWindow :size="18" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0">{{ ui.name }}</div>
                <a :href="ui.url" target="_blank" rel="noopener"
                   class="inline-flex items-center gap-1 text-xs font-medium text-sky-600 hover:underline dark:text-sky-300">
                  Open site <ArrowUpRight :size="12" :stroke-width="1.75" aria-hidden="true" />
                </a>
              </div>
              <Tag :value="observedPhase(ui.state.observed)" :severity="observedPhaseSeverity(ui.state.observed)" />
            </div>
          </li>
          <!-- a microservice as the program it is: its entry point along the top, as a UI shows its address -->
          <li v-for="microservice in microservices" :key="`ms-${microservice.id ?? microservice.name}`"
              class="flex flex-col overflow-hidden rounded-xl border border-surface-200 dark:border-surface-700">
            <div class="flex items-center gap-2 border-b border-surface-200 bg-surface-50 px-3 py-2 dark:border-surface-700 dark:bg-surface-800/60">
              <SquareTerminal :size="13" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
              <span class="truncate font-mono text-[0.6875rem] text-surface-600 dark:text-surface-300"
                    v-tooltip.top="microservice.entryPoint ?? undefined">{{ microservice.entryPoint ?? 'Entry point not reported' }}</span>
            </div>
            <div class="flex flex-1 items-center gap-3 px-4 py-4">
              <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TINTS.purple]">
                <Server :size="18" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0">{{ microservice.name }}</div>
                <div class="flex items-center gap-1.5 text-xs text-muted-color">
                  Microservice
                  <template v-if="microservice.state.observed?.commitSha">
                    <span aria-hidden="true">·</span>
                    <span class="font-mono" v-tooltip.top="microservice.state.observed.commitSha">{{ shortSha(microservice.state.observed.commitSha) }}</span>
                  </template>
                </div>
              </div>
              <Tag :value="observedPhase(microservice.state.observed)" :severity="observedPhaseSeverity(microservice.state.observed)" />
            </div>
          </li>
        </ul>
      </section>

      <section :class="cardClass">
        <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">About</h2>
        <FactList :facts="facts" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch } from 'vue'
import { AppWindow, ArrowUpRight, Boxes, SquareTerminal, CalendarClock, ChevronRight, FileCode, GitBranch, Globe, Hash, LayoutGrid, Server, Table } from '@lucide/vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { Kinotic } from '@kinotic-ai/core'
import { type MicroserviceDeployment, type Project, type ProjectDeployment, RepositoryConnectionStatus, type UiDeployment } from '@kinotic-ai/management-api'
import { createDebug, DatetimeUtil, FactList, HeartbeatIcon, observedPhase, observedPhaseSeverity, PageHeader, StatCard, TINTS, EmptyChartCharacter, HEARTBEAT_TINTS, deploymentHeartbeat, shortSha } from '@kinotic-ai/frontend-common'
import NoAccessState from '@/components/access/NoAccessState.vue'
import { isAuthorizationError } from '@/util/access'

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

const debug = createDebug('project-overview')

const cardClass = 'rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-800/30'

const basePath = computed(() => `/application/${encodeURIComponent(props.applicationId)}/project/${encodeURIComponent(props.projectId)}`)

const project = ref<Project | null>(null)
const deployment = ref<ProjectDeployment | null>(null)
/** What the Deployment card's heartbeat shows: beating while running, a dropped beat once failed. */
const heartbeat = computed(() => deploymentHeartbeat(deployment.value?.state.observed?.phase))
const microservices = ref<MicroserviceDeployment[]>([])
const microserviceCount = computed(() => microservices.value.length)
const uis = ref<UiDeployment[]>([])
const entityCount = ref<number | null>(null)
const loadingEntities = ref(true)
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

const deploymentDetail = computed(() => {
  const updated = deployment.value?.updated
  return updated ? `${workloadSummary.value} · ${DatetimeUtil.formatRelativeDate(updated)}` : workloadSummary.value
})

const facts = computed(() => {
  const repository = project.value?.repoFullName
  return [
    { label: 'Project id', icon: markRaw(Hash), value: props.projectId, mono: true },
    { label: 'Application', icon: markRaw(LayoutGrid), value: props.applicationId,
      to: `/application/${encodeURIComponent(props.applicationId)}` },
    { label: 'Repository', icon: markRaw(GitBranch), mono: true,
      value: repository ? `${repository}${project.value?.repoDefaultBranch ? ` · ${project.value.repoDefaultBranch}` : ''}` : '—' },
    { label: 'Source of truth', icon: markRaw(FileCode), value: project.value?.sourceOfTruth ?? '—' },
    { label: 'Updated', icon: markRaw(CalendarClock),
      value: project.value?.updated ? DatetimeUtil.formatRelativeDate(project.value.updated) : null }
  ]
})

watch(() => props.projectId, () => {
  void load()
  void loadEntityCount()
}, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  project.value = null
  deployment.value = null
  microservices.value = []
  uis.value = []
  try {
    const [loadedProject, loadedDeployment, loadedMicroservices, loadedUis] = await Promise.all([
      Kinotic.projects.findById(props.projectId),
      Kinotic.projects.findDeployment(props.projectId),
      Kinotic.microserviceDeployments.findAllForProject(props.projectId),
      Kinotic.uiDeployments.findAllForProject(props.projectId)
    ])
    project.value = loadedProject
    deployment.value = loadedDeployment
    microservices.value = loadedMicroservices
    uis.value = loadedUis
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

// The entity count comes from another service than the rest of the page, so it loads on its own:
// when that service can't be reached, only the Entities card goes without its figure
async function loadEntityCount(): Promise<void> {
  loadingEntities.value = true
  entityCount.value = null
  try {
    entityCount.value = await Kinotic.entityDefinitions.countForProject(props.projectId)
  } catch (err) {
    debug('Failed to count the entities of %s: %O', props.projectId, err)
  } finally {
    loadingEntities.value = false
  }
}
</script>
