<template>
  <div>
    <PageHeader title="Observability" description="The traces and metrics of the organization's workloads." />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="mb-4 flex items-center gap-3">
      <Select
        v-model="applicationId"
        :options="applications"
        optionLabel="name"
        optionValue="id"
        placeholder="All applications"
        showClear
        size="small"
        class="w-72"
      />
    </div>

    <TelemetryPanel :organization-id="organizationId" :application-id="applicationId" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import Message from 'primevue/message'
import Select from 'primevue/select'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application } from '@kinotic-ai/management-api'
import { PageHeader, TelemetryPanel } from '@kinotic-ai/frontend-common'

const props = defineProps<{
  organizationId: string
}>()

/** How many of the organization's applications the filter lists. */
const APPLICATION_PAGE_SIZE = 200

const applications = ref<Application[]>([])
const applicationId = ref<string | null>(null)
const error = ref<string | null>(null)

async function load() {
  error.value = null
  applicationId.value = null
  applications.value = []
  try {
    const page = await Kinotic.systemOrganizations.findApplications(props.organizationId, Pageable.create(0, APPLICATION_PAGE_SIZE))
    applications.value = page.content ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load the organization\'s applications'
  }
}

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, load)

onMounted(load)
</script>
