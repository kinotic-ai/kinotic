<template>
  <div class="flex flex-col">
    <PageHeader title="Trace">
      <template #eyebrow>
        <RouterLink :to="observabilityPath" class="hover:underline">Observability</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate font-mono">{{ traceId }}</span>
      </template>
    </PageHeader>

    <TraceDetail :key="traceId" :organization-id="organizationId ?? null" :trace-id="traceId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, TraceDetail } from '@kinotic-ai/frontend-common'

import { scopePath } from '@/util/scope'

/**
 * One trace opened from an Observability page: the platform's own, an organization's, or an
 * application's. The eyebrow leads back to the page it was opened from.
 */
const props = defineProps<{
  traceId: string
  organizationId?: string
  applicationId?: string
}>()

const observabilityPath = computed(() =>
    `${scopePath({ organizationId: props.organizationId, applicationId: props.applicationId })}/observability`)
</script>
