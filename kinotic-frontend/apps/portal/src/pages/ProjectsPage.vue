<template>
  <div class="flex flex-col">
    <PageHeader title="Projects"
                description="The functional units that make up this application. Each is backed by a GitHub repository." />
    <ProjectList :applicationId="applicationId" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'
import ProjectList from '@/components/ProjectList.vue'

defineProps<{
  applicationId: string
}>()
</script>
