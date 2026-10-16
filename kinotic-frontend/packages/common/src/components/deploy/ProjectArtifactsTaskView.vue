<template>
  <div class="flex flex-col gap-1 text-xs">
    <div>
      <span class="text-muted-color">Microservices:</span>
      <span v-if="data.microservices.length === 0"> none</span>
      <span v-for="microservice in data.microservices" :key="microservice.name" class="ml-2 font-mono">
        {{ microservice.name }}
        <span class="text-muted-color">({{ microservice.dir }}/{{ microservice.entry }})</span>
      </span>
    </div>
    <div>
      <span class="text-muted-color">UIs:</span>
      <span v-if="data.uis.length === 0"> none</span>
      <span v-for="ui in data.uis" :key="ui.name" class="ml-2 font-mono">
        {{ ui.name }} <span class="text-muted-color">({{ ui.dir }})</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { JobRun, ProjectArtifacts } from '@kinotic-ai/management-api'
import type { JobTaskNode } from '../grind/JobTaskNode'

/**
 * The TaskViews.PROJECT_ARTIFACTS view: the artifacts a deployment run found in the deployed
 * commit, each microservice with the module it starts from, and each UI.
 */
defineProps<{
  data: ProjectArtifacts
  node: JobTaskNode
  run: JobRun
}>()
</script>
