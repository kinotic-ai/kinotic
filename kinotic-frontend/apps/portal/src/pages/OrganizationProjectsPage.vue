<template>
  <div class="flex flex-col">
    <PageHeader title="Projects"
                description="Your organization's projects across all of its applications, with the state of each project's repository and last deploy run." />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <ProjectsTable :projects="projects" :runs="runs"
                   :project-route="project => projectPath(project.applicationId, project.id ?? '')"
                   :application-route="applicationPath"
                   :run-route="run => `/jobs/${encodeURIComponent(run.id ?? '')}`" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { JobRun, Project } from '@kinotic-ai/management-api'
import { PageHeader, ProjectsTable, errorMessage, scanJobRuns } from '@kinotic-ai/frontend-common'

import { USER_STATE } from '@/states/IUserState'
import { applicationPath, projectPath } from '@/util/scope'

/**
 * The organization's projects across all of its applications, each with the state of its
 * repository and of its last deploy run. A row opens the project; a run's commit opens the run.
 */

/** How many of the organization's projects the page lists. */
const PROJECT_PAGE_SIZE = 200

const projects = ref<Project[]>([])
const runs = ref<JobRun[]>([])
const error = ref<string | null>(null)

async function load() {
  error.value = null
  try {
    const [page, runList] = await Promise.all([
      Kinotic.projects.findAll(Pageable.create(0, PROJECT_PAGE_SIZE)),
      scanJobRuns({ organizationId: USER_STATE.getOrganizationId() })
    ])
    projects.value = page.content ?? []
    runs.value = runList
  } catch (err) {
    projects.value = []
    error.value = errorMessage(err, 'Failed to load the projects')
  }
}

onMounted(load)
</script>
