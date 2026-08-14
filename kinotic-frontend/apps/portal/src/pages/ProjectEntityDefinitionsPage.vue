<script lang="ts" setup>
import { useRoute } from 'vue-router'
import { computed, watch } from 'vue'
import ProjectEntityDefinitionsTable from '@/components/ProjectEntityDefinitionsTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('project-entity-definitions-page')

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)
const applicationId = computed(() => APPLICATION_STATE.currentApplication?.id || '')

watch(() => APPLICATION_STATE.currentApplication, (newApp) => {
  debug('APPLICATION_STATE.currentApplication changed to: %s', newApp?.id)
}, { deep: true })

watch(applicationId, (newId) => {
  debug('applicationId computed changed to: %s', newId)
})
</script>

<template>
  <div class="flex flex-col">
    <PageHeader :title="projectId" />
    <ProjectEntityDefinitionsTable :key="`${applicationId}-${projectId}`" :applicationId="applicationId" />
  </div>
</template>
