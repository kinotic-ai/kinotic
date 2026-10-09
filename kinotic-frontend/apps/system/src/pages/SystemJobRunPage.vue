<template>
  <div class="flex flex-col">
    <PageHeader title="System job run">
      <template #eyebrow>
        <RouterLink to="/system-jobs" class="hover:underline">System jobs</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="inline-flex min-w-0 items-center rounded-full border border-surface-200 bg-surface-100 px-2.5 py-0.5 font-mono text-xs text-surface-600 dark:border-surface-700 dark:bg-surface-800 dark:text-surface-300"><span class="truncate">{{ jobRunId }}</span></span>
      </template>
    </PageHeader>

    <SystemJobRunDetail :job-run-id="jobRunId" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'

import SystemJobRunDetail from '@/components/SystemJobRunDetail.vue'

/** One system job run, opened from System jobs or right after starting it there. */
defineProps<{
  jobRunId: string
}>()
</script>
