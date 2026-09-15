<template>
  <div class="flex flex-col">
    <PageHeader title="Observability" :description="description" />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="organizationId && !applicationId" class="mb-4 flex items-center gap-3">
      <Select
        v-model="selectedApplicationId"
        :options="applications"
        option-label="name"
        option-value="id"
        placeholder="All applications"
        show-clear
        size="small"
        class="w-72"
      />
    </div>

    <TelemetryPanel :organization-id="organizationId ?? null" :application-id="applicationId ?? selectedApplicationId" :trace-route="traceRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Message from 'primevue/message'
import Select from 'primevue/select'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Application } from '@kinotic-ai/management-api'
import { PageHeader, TelemetryPanel, errorMessage } from '@kinotic-ai/frontend-common'

import { scopePath } from '@/util/scope'

/**
 * The traces and metrics of the scope the route names: the system tenant for the platform
 * itself, an organization's across all of its applications or one of them, or one
 * application's. A trace opens on its own page under this one.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
}>()

/** How many of the organization's applications the filter lists. */
const APPLICATION_PAGE_SIZE = 200

const applications = ref<Application[]>([])
const selectedApplicationId = ref<string | null>(null)
const error = ref<string | null>(null)

const description = computed(() => {
  let ret: string
  if (props.applicationId) {
    ret = `The traces and metrics of ${props.applicationId}'s workloads, the same view its organization has.`
  } else if (props.organizationId) {
    ret = `The traces and metrics of ${props.organizationId}'s workloads, across all of its applications or one of them.`
  } else {
    ret = 'The traces and metrics of the platform itself: the servers, the gateway, and the worker nodes, from the system tenant.'
  }
  return ret
})

function traceRoute(traceId: string): RouteLocationRaw {
  return `${scopePath({ organizationId: props.organizationId, applicationId: props.applicationId })}/observability/traces/${encodeURIComponent(traceId)}`
}

async function loadApplications() {
  error.value = null
  selectedApplicationId.value = null
  applications.value = []
  if (!props.organizationId || props.applicationId) {
    return
  }
  try {
    const page = await Kinotic.systemOrganizations.findApplications(props.organizationId, Pageable.create(0, APPLICATION_PAGE_SIZE))
    applications.value = page.content ?? []
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the organization\'s applications')
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId], loadApplications, { immediate: true })
</script>
