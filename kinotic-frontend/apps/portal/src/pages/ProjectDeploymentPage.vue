<template>
  <div class="flex flex-col">
    <PageHeader title="Deployment">
      <template #actions>
        <Button v-if="deployment?.lastJobRunId"
                label="View in jobs" icon="pi pi-external-link" severity="secondary" outlined
                @click="router.push({ name: 'job-run', params: { jobRunId: deployment.lastJobRunId } })" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <div v-if="!loading && !deployment" class="p-6 text-sm text-muted-color">
      This project has never been deployed. Pushing to its repository's default branch deploys it.
    </div>

    <template v-if="deployment">
      <div class="mb-4 flex flex-wrap items-center gap-4">
        <Tag :value="deployment.status.type" :severity="deploymentStatusSeverity(deployment.status.type)" />
        <span v-if="deployment.commitSha" class="font-mono text-sm text-muted-color"
              :title="deployment.commitSha">{{ deployment.commitSha.slice(0, 12) }}</span>
        <span v-if="deployment.updated" class="text-xs text-muted-color">
          Updated {{ DatetimeUtil.formatRelativeDate(deployment.updated) }}
        </span>
      </div>

      <Message v-if="deployment.status.type === StatusType.FAILED && deployment.status.message"
               severity="error" :closable="false">{{ deployment.status.message }}</Message>

      <JobRunProgress v-if="deployment.lastJobRunId"
                      :key="deployment.lastJobRunId"
                      :job-run-id="deployment.lastJobRunId" />
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading deployment…</div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { DatetimeUtil, JobRunProgress, PageHeader } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import { ProjectDeploymentStatusType, type ProjectDeployment } from '@kinotic-ai/management-api'

/**
 * The project's deployment: current status and commit, and the latest deployment job's
 * steps rendered live by JobRunProgress. Polls the deployment record so a new push
 * swaps in its job run while the page is open.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const POLL_INTERVAL_MS = 5000

const router = useRouter()
const StatusType = ProjectDeploymentStatusType

const deployment = ref<ProjectDeployment | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function loadDeployment(): Promise<void> {
  try {
    deployment.value = await Kinotic.projectDeployments.findByProjectId(props.projectId)
    error.value = null
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function deploymentStatusSeverity(type: ProjectDeploymentStatusType): string {
  let ret: string
  if (type === ProjectDeploymentStatusType.RUNNING) {
    ret = 'success'
  } else if (type === ProjectDeploymentStatusType.FAILED) {
    ret = 'danger'
  } else {
    ret = 'info'
  }
  return ret
}

const pollTimer = setInterval(() => { void loadDeployment() }, POLL_INTERVAL_MS)
onUnmounted(() => clearInterval(pollTimer))
void loadDeployment()
</script>
