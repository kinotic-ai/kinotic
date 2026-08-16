<template>
  <div class="flex flex-col">
    <PageHeader title="Job run">
      <template #actions>
        <Button label="All jobs" icon="pi pi-arrow-left" severity="secondary" outlined
                @click="router.push({ name: 'jobs' })" />
      </template>
    </PageHeader>

    <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { JobRunProgress, PageHeader } from '@kinotic-ai/frontend-common'

defineProps<{
  jobRunId: string
}>()

const router = useRouter()
</script>
