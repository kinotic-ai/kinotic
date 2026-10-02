<template>
  <div class="flex flex-col">
    <PageHeader title="Observability" description="The traces and metrics of this application's workloads." />

    <TelemetryPanel :organization-id="organizationId" :application-id="applicationId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, TelemetryPanel } from '@kinotic-ai/frontend-common'
import { USER_STATE } from '@/states/IUserState'

/** One application's traces and metrics. */
defineProps<{
  applicationId: string
}>()

const organizationId = computed(() => USER_STATE.getOrganizationId())
</script>
