<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Jobs</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="font-mono">{{ jobRunId }}</span>
      </template>
      <template #actions>
        <Button v-if="projectDeploymentPath" label="Open in project" icon="pi pi-folder" severity="secondary" outlined
                @click="router.push(projectDeploymentPath)" />
      </template>
    </PageHeader>

    <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="ProjectDeployStores.hasDetail">
      <template #detail="{ node, root }">
        <ProjectDeployTaskDetail :organization-id="organizationId" :node="node" :root="root" />
      </template>
    </JobRunProgress>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { Kinotic } from '@kinotic-ai/core'
import { createDebug, JobRunProgress, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail } from '@kinotic-ai/frontend-common'
import { KinoticStates } from '@/states'
import { projectPath, scopePath } from '@/util/scope'

const debug = createDebug('job-run-page')

/**
 * One job run opened from a Jobs list — the organization's, an application's, or a project's.
 * The eyebrow leads back to that list; a run that belongs to a project also offers the jump to
 * that project's Deployment page.
 */
const props = defineProps<{
  jobRunId: string
  applicationId?: string
  projectId?: string
}>()

const router = useRouter()
const organizationId = KinoticStates.getUserState().getOrganizationId()

const listPath = computed(() => `${scopePath({ applicationId: props.applicationId, projectId: props.projectId })}/jobs`)

const projectDeploymentPath = ref<string | null>(null)

watch(() => props.jobRunId, loadOwningProject, { immediate: true })

/** Resolves the run's project so the page can offer a way into it. */
async function loadOwningProject(): Promise<void> {
  projectDeploymentPath.value = null
  try {
    const run = await Kinotic.jobMonitoring.findJobRun(props.jobRunId)
    if (run.applicationId && run.projectId) {
      projectDeploymentPath.value = `${projectPath(run.applicationId, run.projectId)}/deployment`
    }
  } catch (error) {
    debug('Failed to load job run %s: %O', props.jobRunId, error)
  }
}
</script>
