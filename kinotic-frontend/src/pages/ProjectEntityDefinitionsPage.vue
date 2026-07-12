<script lang="ts" setup>
import { useRoute } from 'vue-router'
import { computed, watch } from 'vue'
import ProjectEntityDefinitionsTable from '@/components/ProjectEntityDefinitionsTable.vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { createDebug } from '@/util/debug'
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('project-entity-definitions-page')

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)
const applicationId = computed(() => APPLICATION_STATE.currentApplication?.id || '')
const isDark = computed(() => darkMode.value)

watch(() => APPLICATION_STATE.currentApplication, (newApp) => {
  debug('APPLICATION_STATE.currentApplication changed to: %s', newApp?.id)
}, { deep: true })

watch(applicationId, (newId) => {
  debug('applicationId computed changed to: %s', newId)
})
</script>

<template>
  <div class="flex flex-col">
    <h1 :class="['mb-4 text-2xl font-semibold', isDark ? 'text-white' : 'text-surface-950']">
      {{ projectId }}
    </h1>
    <ProjectEntityDefinitionsTable :key="`${applicationId}-${projectId}`" :applicationId="applicationId" />
  </div>
</template>
