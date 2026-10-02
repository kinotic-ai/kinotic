<template>
  <TableChip :icon="ProjectsIcon" :to="`/application/${encodeURIComponent(applicationId)}/projects`">
    <span v-if="failed">—</span>
    <Skeleton v-else-if="count === null" width="1.5rem" height="0.75rem" />
    <span v-else>{{ count }} {{ count === 1 ? 'project' : 'projects' }}</span>
  </TableChip>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Skeleton from 'primevue/skeleton'
import { Kinotic } from '@kinotic-ai/core'
import { createDebug, ProjectsIcon, TableChip } from '@kinotic-ai/frontend-common'

/** How many projects an application has, as a chip leading to its projects list. */
const props = defineProps<{
  applicationId: string
}>()

const debug = createDebug('application-projects-link')

const count = ref<number | null>(null)
const failed = ref(false)

onMounted(async () => {
  try {
    count.value = await Kinotic.projects.countForApplication(props.applicationId)
  } catch (error) {
    debug('Failed to count the projects of %s: %O', props.applicationId, error)
    failed.value = true
  }
})
</script>
