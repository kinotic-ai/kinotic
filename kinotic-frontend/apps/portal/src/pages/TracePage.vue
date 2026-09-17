<template>
  <div class="flex flex-col">
    <PageHeader title="Trace">
      <template #eyebrow>
        <RouterLink :to="observabilityPath" class="hover:underline">Observability</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate font-mono">{{ traceId }}</span>
      </template>
    </PageHeader>

    <TraceDetail :key="traceId" :organization-id="organizationId" :trace-id="traceId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, TraceDetail } from '@kinotic-ai/frontend-common'
import { USER_STATE } from '@/states/IUserState'

/**
 * One trace opened from an Observability page: the organization's, or an application's when
 * {@code applicationId} is given. The eyebrow leads back to the page it was opened from.
 */
const props = defineProps<{
  traceId: string
  applicationId?: string
}>()

const organizationId = computed(() => USER_STATE.getOrganizationId())

const observabilityPath = computed(() => props.applicationId
    ? `/application/${encodeURIComponent(props.applicationId)}/observability`
    : '/observability')
</script>
