<template>
  <div class="flex flex-col">
    <PageHeader title="Jobs"
                description="Job runs executed for your organization, such as workload deployments, step by step.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined @click="jobRunsTable?.refresh()" />
      </template>
    </PageHeader>

    <JobRunsTable ref="jobRunsTable" :run-route="runRoute">
      <template #run="{ jobRunId }">
        <JobRunDetail :job-run-id="jobRunId" />
      </template>
    </JobRunsTable>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Button from 'primevue/button'
import { JobRunsTable, PageHeader } from '@kinotic-ai/frontend-common'
import JobRunDetail from '@/components/JobRunDetail.vue'

const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()

function runRoute(jobRunId: string): RouteLocationRaw {
  return { name: 'job-run', params: { jobRunId } }
}
</script>
