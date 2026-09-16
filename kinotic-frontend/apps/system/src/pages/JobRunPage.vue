<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Jobs</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate font-mono">{{ jobRunId }}</span>
      </template>
      <template #actions>
        <Button v-if="owningOrganizationPath" :label="`Open ${owningOrganizationId}`" icon="pi pi-building" severity="secondary" outlined
                @click="router.push(owningOrganizationPath)" />
      </template>
    </PageHeader>

    <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="ProjectDeployStores.hasDetail">
      <template #detail="{ node, root }">
        <ProjectDeployTaskDetail :node="node" :root="root" />
      </template>
    </JobRunProgress>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

import { Kinotic } from '@kinotic-ai/core'
import { JobRunProgress, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail, createDebug } from '@kinotic-ai/frontend-common'

import { organizationPath, scopePath, type Scope } from '@/util/scope'

const debug = createDebug('job-run-page')

/**
 * One job run opened from a Jobs list. The eyebrow leads back to that list; on the platform a
 * run that belongs to an organization also offers the jump into that organization.
 */
const props = defineProps<{
  jobRunId: string
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const router = useRouter()

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const listPath = computed(() => `${scopePath(scope.value)}/jobs`)

const owningOrganizationId = ref<string | null>(null)
const owningOrganizationPath = computed(() => owningOrganizationId.value ? organizationPath(owningOrganizationId.value) : null)

watch(() => props.jobRunId, loadOwningOrganization, { immediate: true })

/** Resolves the run's organization so the platform view can offer a way into it. */
async function loadOwningOrganization(): Promise<void> {
  owningOrganizationId.value = null
  if (scope.value.organizationId) {
    return
  }
  try {
    const run = await Kinotic.jobMonitoring.findJobRun(props.jobRunId)
    owningOrganizationId.value = run.organizationId
  } catch (error) {
    debug('Failed to load job run %s: %O', props.jobRunId, error)
  }
}
</script>
