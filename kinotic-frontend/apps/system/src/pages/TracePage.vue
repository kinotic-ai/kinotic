<template>
  <div class="flex flex-col">
    <PageHeader title="Trace">
      <template #eyebrow>
        <RouterLink :to="observabilityPath" class="hover:underline">Observability</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate font-mono">{{ traceId }}</span>
      </template>
    </PageHeader>

    <TraceDetail :key="traceId" :organization-id="organizationId ?? null" :trace-id="traceId" :range="range" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PageHeader } from '@kinotic-ai/frontend-common'
import { TraceDetail, traceWindowFromQuery } from '@kinotic-ai/frontend-common/management'

import { scopePath } from '@/util/scope'

/**
 * One trace opened from an Observability page: the system's own, an organization's, or an
 * application's, looked up in the time window the link's query names. The eyebrow leads back to
 * the page it was opened from.
 */
const props = defineProps<{
  traceId: string
  organizationId?: string
  applicationId?: string
}>()

const route = useRoute()
const range = computed(() => traceWindowFromQuery(route.query))

const observabilityPath = computed(() =>
    `${scopePath({ organizationId: props.organizationId, applicationId: props.applicationId })}/observability`)
</script>
