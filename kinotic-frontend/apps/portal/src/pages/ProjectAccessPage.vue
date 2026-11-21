<template>
  <div class="flex flex-col">
    <PageHeader title="Access" description="Who may work with this project and its entity definitions." />
    <AccessPanel :resource="resource" :tint="TINTS.purple" :application-id="applicationId" :project-id="projectId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import AccessPanel from '@/components/access/AccessPanel.vue'

/** The Access page of one project: grants on it reach every entity definition inside it. */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const resource = computed(() => ({ type: 'project', id: props.projectId }))
</script>
