<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <PageHeader :title="title" :description="description || 'This section is being prepared.'" />
</template>
