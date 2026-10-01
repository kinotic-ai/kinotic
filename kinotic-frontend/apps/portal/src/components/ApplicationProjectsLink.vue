<template>
  <RouterLink
    :to="`/application/${encodeURIComponent(applicationId)}/projects`"
    class="inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-0.5 font-sans text-xs font-medium text-surface-700 transition-colors hover:bg-surface-200 hover:text-surface-950 dark:bg-surface-800 dark:text-surface-200 dark:hover:bg-surface-700 dark:hover:text-surface-0"
    @click.stop
  >
    <ProjectsIcon :size="13" :stroke-width="1.75" aria-hidden="true" />
    <span v-if="failed">—</span>
    <Skeleton v-else-if="count === null" width="1.5rem" height="0.75rem" />
    <span v-else class="tabular-nums">{{ count }} {{ count === 1 ? 'project' : 'projects' }}</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Skeleton from 'primevue/skeleton'
import { Kinotic } from '@kinotic-ai/core'
import { createDebug, ProjectsIcon } from '@kinotic-ai/frontend-common'

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
