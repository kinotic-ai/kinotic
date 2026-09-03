<template>
  <div class="flex flex-col gap-1 text-xs">
    <div>
      <span class="text-muted-color">Microservices:</span>
      <span v-if="artifacts.microservices.length === 0"> none</span>
      <span v-for="microservice in artifacts.microservices" :key="microservice.name" class="ml-2 font-mono">
        {{ microservice.name }}
        <span class="text-muted-color">({{ microservice.dir }}/{{ microservice.entry }})</span>
      </span>
    </div>
    <div>
      <span class="text-muted-color">UIs:</span>
      <span v-if="artifacts.uis.length === 0"> none</span>
      <span v-for="ui in artifacts.uis" :key="ui.name" class="ml-2 font-mono">
        {{ ui.name }} <span class="text-muted-color">({{ ui.dir }})</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ProjectArtifacts } from './ProjectDeployStores'

/**
 * The artifacts a deployment run found in the deployed commit, as the detail of the task
 * that bound them into the run: each microservice with the module it starts from, and each UI.
 */
defineProps<{
  artifacts: ProjectArtifacts
}>()
</script>
