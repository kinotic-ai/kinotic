<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <PageHeader :title="title" :description="description || 'This section is being prepared.'" />
</template>
