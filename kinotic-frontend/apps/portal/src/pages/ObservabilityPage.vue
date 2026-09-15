<template>
  <div class="flex flex-col">
    <PageHeader title="Observability" description="The traces and metrics of this application's workloads." />

    <TelemetryPanel :organization-id="organizationId" :application-id="applicationId" :trace-route="traceRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { PageHeader, TelemetryPanel } from '@kinotic-ai/frontend-common'
import { USER_STATE } from '@/states/IUserState'

/** One application's traces and metrics; a trace opens on its own page under this one. */
const props = defineProps<{
  applicationId: string
}>()

const organizationId = computed(() => USER_STATE.getOrganizationId())

function traceRoute(traceId: string): RouteLocationRaw {
  return { name: 'application-trace', params: { applicationId: props.applicationId, traceId } }
}
</script>
