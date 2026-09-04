<template>
  <div class="flex flex-col">
    <PageHeader title="Entities"
                description="The entity definitions that make up this project's data model." />
    <EntityDefinitionsList :key="`${applicationId}/${projectId}`" :applicationId="applicationId" :projectId="projectId" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'
import EntityDefinitionsList from '@/components/EntityDefinitionsList.vue'

defineProps<{
  applicationId: string
  projectId: string
}>()
</script>
