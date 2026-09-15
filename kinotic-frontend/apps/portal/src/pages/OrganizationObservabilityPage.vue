<template>
  <div class="flex flex-col">
    <PageHeader title="Observability" description="The traces and metrics of your organization's workloads.">
      <template #actions>
        <Select
          v-model="applicationId"
          :options="APPLICATION_STATE.allApplications"
          optionLabel="id"
          optionValue="id"
          placeholder="All applications"
          showClear
          size="small"
          class="w-64"
        />
      </template>
    </PageHeader>

    <TelemetryPanel :organization-id="organizationId" :application-id="applicationId" :trace-route="traceRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Select from 'primevue/select'
import { PageHeader, TelemetryPanel } from '@kinotic-ai/frontend-common'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'

/**
 * The organization's traces and metrics across every application, narrowed to one with the
 * filter; a trace opens on its own page under this one.
 */
const organizationId = computed(() => USER_STATE.getOrganizationId())
const applicationId = ref<string | null>(null)

onMounted(() => {
  if (APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications()
  }
})

function traceRoute(traceId: string): RouteLocationRaw {
  return { name: 'organization-trace', params: { traceId } }
}
</script>
