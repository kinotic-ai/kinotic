<template>
  <div class="flex flex-col">
    <PageHeader title="Jobs"
                description="Job runs executed for your organization, such as workload deployments, step by step.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined @click="jobRunsTable?.refresh()" />
      </template>
    </PageHeader>

    <JobRunsTable ref="jobRunsTable" @open="openRun" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { JobRunsTable, PageHeader } from '@kinotic-ai/frontend-common'

const router = useRouter()
const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()

function openRun(jobRunId: string) {
  router.push({ name: 'job-run', params: { jobRunId } })
}
</script>
