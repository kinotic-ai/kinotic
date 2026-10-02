<template>
  <div class="flex flex-col">
    <PageHeader title="Change log"
                description="What happened to every node, workload, deployment and job run on the platform, newest first, with what caused it.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
                :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <WatchEventsTimeline :entries="entries" show-record empty-text="Nothing has happened on the platform yet." />

    <TablePaginator v-if="total > 0" class="mt-3"
                    :first="first" :rows="rows" :total-records="total" :rows-per-page-options="ROWS_PER_PAGE"
                    @page="changePage" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import type { PageState } from 'primevue/paginator'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { WatchEvent } from '@kinotic-ai/management-api'
import { PageHeader, TablePaginator, WatchEventsTimeline, errorMessage } from '@kinotic-ai/frontend-common'

const ROWS_PER_PAGE = [25, 50, 100]

const entries = ref<WatchEvent[]>([])
const total = ref(0)
const first = ref(0)
const rows = ref(50)
const loading = ref(false)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await Kinotic.watchEvents.findAll(Pageable.create(Math.floor(first.value / rows.value), rows.value))
    entries.value = page.content ?? []
    total.value = page.totalElements ?? 0
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the change log')
  } finally {
    loading.value = false
  }
}

function changePage(state: PageState) {
  first.value = state.first
  rows.value = state.rows
  load()
}

onMounted(load)
</script>
