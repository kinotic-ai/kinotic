<template>
  <div class="flex flex-col">
    <PageHeader title="Access" description="Who may work with this application and what is inside it." />
    <AccessPanel :resource="resource" :tint="TINTS.blue" :application-id="applicationId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import AccessPanel from '@/components/access/AccessPanel.vue'

/** The Access page of one application: grants on it reach every project and entity definition inside it. */
const props = defineProps<{
  applicationId: string
}>()

const resource = computed(() => ({ type: 'application', id: props.applicationId }))
</script>
