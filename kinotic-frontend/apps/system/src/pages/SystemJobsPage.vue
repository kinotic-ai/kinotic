<template>
  <div class="flex flex-col">
    <PageHeader title="System jobs" description="Platform jobs an operator can run on demand, and the runs they made, task by task.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="refresh" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="jobs.length === 0 && !loading"
         class="mb-6 rounded-xl border border-dashed border-surface-200 p-6 text-sm text-muted-color dark:border-surface-700">
      No system jobs are available
    </div>
    <div v-else class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="job in jobs" :key="job.name"
           class="flex flex-col gap-3 rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex items-start gap-3">
          <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', TINTS.ink]">
            <Workflow :size="18" :stroke-width="1.75" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <h2 class="min-w-0 truncate font-mono text-sm font-semibold text-surface-950 dark:text-surface-0">{{ job.name }}</h2>
              <span v-if="job.version" class="shrink-0 rounded-md bg-surface-100 px-1.5 py-0.5 text-xs text-surface-600 dark:bg-surface-800 dark:text-surface-300">v{{ job.version }}</span>
            </div>
            <p v-if="job.description" class="mt-1 text-sm text-muted-color">{{ job.description }}</p>
          </div>
        </div>
        <div class="mt-auto flex justify-end">
          <Button label="Run" icon="pi pi-play" size="small" :loading="starting === job.name" :disabled="starting !== null"
                  @click="confirmStart(job)" />
        </div>
      </div>
    </div>

    <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-color">Runs</h2>
    <JobRunsTable ref="jobRunsTable" :organization-id="null" :run-route="runRoute">
      <template #run="{ jobRunId }">
        <SystemJobRunDetail :job-run-id="jobRunId" />
      </template>
    </JobRunsTable>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Workflow } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { SystemJobDescriptor } from '@kinotic-ai/system-api'
import { JobRunsTable, PageHeader, showErrorToast, TINTS } from '@kinotic-ai/frontend-common'

import SystemJobRunDetail from '@/components/SystemJobRunDetail.vue'

/**
 * The platform's system jobs, each with a button that starts a run of it and opens the run, above
 * the runs the platform owns: the system jobs' runs, newest first.
 */
const router = useRouter()
const confirm = useConfirm()
const toast = useToast()

const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()
const jobs = ref<SystemJobDescriptor[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
// The name of the job whose run is starting; one start at a time
const starting = ref<string | null>(null)

async function loadJobs(): Promise<void> {
  loading.value = true
  try {
    jobs.value = await Kinotic.systemJobs.findSystemJobs()
    error.value = null
  } catch (err) {
    error.value = `Could not list the system jobs: ${err instanceof Error ? err.message : String(err)}`
  } finally {
    loading.value = false
  }
}

function refresh(): void {
  jobRunsTable.value?.refresh()
  void loadJobs()
}

function confirmStart(job: SystemJobDescriptor): void {
  confirm.require({
    header: 'Run system job',
    message: `Run ${job.name}?${job.description ? ` ${job.description}.` : ''}`,
    icon: 'pi pi-play',
    acceptProps: { label: 'Run' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => start(job)
  })
}

async function start(job: SystemJobDescriptor): Promise<void> {
  starting.value = job.name
  try {
    const run = await Kinotic.systemJobs.startSystemJob(job.name)
    if (run.id) {
      await router.push(runRoute(run.id))
    }
  } catch (err) {
    showErrorToast(toast, `Failed to run ${job.name}`, err)
  } finally {
    starting.value = null
  }
}

function runRoute(jobRunId: string): string {
  return `/system-jobs/${encodeURIComponent(jobRunId)}`
}

onMounted(loadJobs)
</script>
