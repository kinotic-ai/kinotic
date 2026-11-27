<template>
  <div class="flex flex-col">
    <PageHeader title="Change log"
                description="What happened to every node, workload, deployment and job run in the system, newest first, with what caused it.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
                :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <StatusChips v-model="recordFilter" :chips="chips" class="mb-4" />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <WatchEventsTimeline :entries="entries" show-record show-scope paged :record-link="recordLink"
                         empty-text="Nothing has happened in the system yet." />

    <TablePaginator v-if="total > 0" class="mt-3"
                    :first="first" :rows="pageSize" :total-records="total" :rows-per-page-options="ROWS_PER_PAGE"
                    @page="onPage" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WatchedType, type WatchEvent } from '@kinotic-ai/management-api'
import { PageHeader, StatusChips, TablePaginator, errorMessage, usePaging, type StatusChip } from '@kinotic-ai/frontend-common'
import { WatchEventsTimeline } from '@kinotic-ai/frontend-common/management'

import { organizationPath } from '@/util/scope'

/**
 * Every watched record's ledger entries, newest first, narrowed to one kind of record by the chip
 * row, which the URL keeps (?records=jobs). Each entry names its record, opening its page where
 * the console has one.
 */

const ROWS_PER_PAGE = [25, 50, 100]

// The kinds of record each chip lists; a project's deployment and the microservices and UIs it made read as one
const RECORD_FILTERS: Record<string, { label: string, types: WatchedType[] }> = {
  deployments: {
    label: 'Deployments',
    types: [WatchedType.PROJECT_DEPLOYMENT, WatchedType.MICROSERVICE_DEPLOYMENT, WatchedType.UI_DEPLOYMENT]
  },
  workloads: { label: 'Workloads', types: [WatchedType.WORKLOAD] },
  jobs: { label: 'Job runs', types: [WatchedType.JOB_RUN] },
  nodes: { label: 'Nodes', types: [WatchedType.VM_NODE] }
}

const route = useRoute()
const router = useRouter()

const entries = ref<WatchEvent[]>([])
const total = ref(0)
const { first, pageSize, page: pageShown, onPage } = usePaging(50, load)
const loading = ref(false)
const error = ref<string | null>(null)

const recordFilter = computed<string | null>({
  get: () => {
    const records = route.query.records
    return typeof records === 'string' && records in RECORD_FILTERS ? records : null
  },
  set: value => router.replace({ query: { ...route.query, records: value ?? undefined } })
})

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null },
  ...Object.entries(RECORD_FILTERS).map(([value, filter]) => ({ label: filter.label, value }))
])

/** The page of the entry's record: a node's, or a workload's or job run's under the organization that owns it. */
function recordLink(entry: WatchEvent): string | null {
  const owner = entry.scope ? organizationPath(entry.scope) : ''
  let ret: string | null
  if (entry.type === WatchedType.VM_NODE) {
    ret = `/worker-nodes/${encodeURIComponent(entry.id)}`
  } else if (entry.type === WatchedType.WORKLOAD) {
    ret = `${owner}/workloads/${encodeURIComponent(entry.id)}`
  } else if (entry.type === WatchedType.JOB_RUN) {
    ret = `${owner}/jobs/${encodeURIComponent(entry.id)}`
  } else {
    ret = null
  }
  return ret
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const types = recordFilter.value ? RECORD_FILTERS[recordFilter.value].types : null
    const page = await Kinotic.watchEvents.findAll(types, Pageable.create(pageShown.value, pageSize.value))
    entries.value = page.content ?? []
    total.value = page.totalElements ?? 0
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the change log')
  } finally {
    loading.value = false
  }
}

// Another kind of record starts its listing from the first page
watch(recordFilter, () => {
  first.value = 0
  load()
})

onMounted(load)
</script>
