<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #eyebrow>
        <RouterLink to="/jobs" class="hover:underline">Jobs</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="inline-flex min-w-0 items-center rounded-full border border-surface-200 bg-surface-100 px-2.5 py-0.5 font-mono text-xs text-surface-600 dark:border-surface-700 dark:bg-surface-800 dark:text-surface-300"><span class="truncate">{{ jobRunId }}</span></span>
      </template>
      <template #actions>
        <Button v-if="projectDeploymentPath" label="Open in project" icon="pi pi-folder" severity="secondary" outlined
                @click="router.push(projectDeploymentPath)" />
      </template>
    </PageHeader>

    <JobRunDetail :job-run-id="jobRunId" @project="projectDeploymentPath = $event" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { PageHeader } from '@kinotic-ai/frontend-common'
import JobRunDetail from '@/components/JobRunDetail.vue'

/**
 * One job run opened from the organization's Jobs list. The eyebrow leads back to that
 * list; a run that belongs to a project also offers the jump to that project's Deployment page.
 */
defineProps<{
  jobRunId: string
}>()

const router = useRouter()

const projectDeploymentPath = ref<string | null>(null)
</script>
