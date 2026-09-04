<template>
  <div class="flex flex-col">
    <PageHeader title="Entities"
                description="Every entity definition across this application's projects." />
    <EntityDefinitionsList :key="applicationId" :applicationId="applicationId" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'
import EntityDefinitionsList from '@/components/EntityDefinitionsList.vue'

defineProps<{
  applicationId: string
}>()
</script>
