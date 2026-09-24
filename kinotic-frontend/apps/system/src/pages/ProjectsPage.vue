<template>
  <div class="flex flex-col">
    <PageHeader title="Projects" :description="description" />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <ProjectsTable :projects="projects" :runs="runs" :application-id="applicationId"
                   :project-route="projectRoute" :application-route="applicationRoute" :run-route="runRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { JobRun, Project } from '@kinotic-ai/management-api'
import { PageHeader, ProjectsTable, errorMessage, scanJobRuns } from '@kinotic-ai/frontend-common'

import { applicationPath, projectPath, scopePath } from '@/util/scope'

/**
 * The projects of an organization across all of its applications, or of one application, each
 * with the state of its repository and of its last deploy run. A row opens the project.
 */
const props = defineProps<{
  organizationId: string
  applicationId?: string
}>()

/** How many of the organization's projects the page lists. */
const PROJECT_PAGE_SIZE = 200

const description = computed(() => props.applicationId
    ? 'The functional units that make up this application, each backed by a GitHub repository, with the state of its last deploy run.'
    : 'The organization\'s projects across all of its applications, with the state of each project\'s last deploy run.')

function projectRoute(project: Project): string {
  return projectPath(props.organizationId, project.applicationId, project.id ?? '')
}

function applicationRoute(applicationId: string): string {
  return applicationPath(props.organizationId, applicationId)
}

function runRoute(run: JobRun): string {
  return `${scopePath({ organizationId: props.organizationId, applicationId: props.applicationId })}/jobs/${encodeURIComponent(run.id ?? '')}`
}

const projects = ref<Project[]>([])
const runs = ref<JobRun[]>([])
const error = ref<string | null>(null)

async function load() {
  error.value = null
  try {
    const [page, runList] = await Promise.all([
      Kinotic.systemOrganizations.findProjects(props.organizationId, Pageable.create(0, PROJECT_PAGE_SIZE)),
      scanJobRuns({ organizationId: props.organizationId, applicationId: props.applicationId })
    ])
    projects.value = (page.content ?? []).filter(project => !props.applicationId || project.applicationId === props.applicationId)
    runs.value = runList
  } catch (err) {
    projects.value = []
    error.value = errorMessage(err, 'Failed to load the projects')
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], load, { immediate: true })
</script>
