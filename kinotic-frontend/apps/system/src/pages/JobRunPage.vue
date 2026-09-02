<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #actions>
        <Button label="All jobs" icon="pi pi-arrow-left" severity="secondary" outlined
                @click="router.push({ name: 'jobs' })" />
      </template>
    </PageHeader>
    <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="ProjectDeployStores.hasWorkloadLog">
      <template #detail="{ node, root }">
        <WorkloadLogView v-if="ProjectDeployStores.workloadLogOf(node, root)"
                         :key="ProjectDeployStores.workloadLogOf(node, root)!"
                         :workload-id="ProjectDeployStores.workloadLogOf(node, root)!" />
        <span v-else class="text-xs text-muted-color">Waiting for the deployment target</span>
      </template>
    </JobRunProgress>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { JobRunProgress, PageHeader } from '@kinotic-ai/frontend-common'
import WorkloadLogView from '@/components/WorkloadLogView.vue'
import ProjectDeployStores from '@/ProjectDeployStores'

defineProps<{
  jobRunId: string
}>()

const router = useRouter()
</script>
