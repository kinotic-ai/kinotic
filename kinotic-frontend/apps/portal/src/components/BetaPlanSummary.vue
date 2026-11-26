<template>
  <div v-if="limits" class="flex flex-col gap-3 text-sm">
    <div>
      <p class="font-semibold text-surface-950 dark:text-surface-0">Kinotic free beta</p>
      <p class="mt-1 text-[0.8125rem] text-surface-600 dark:text-surface-300">{{ limits.summary }}</p>
    </div>
    <ul class="flex flex-col gap-1.5 text-[0.8125rem]">
      <li v-if="limits.maxApplicationsPerOrganization !== null" class="flex items-center justify-between gap-4">
        <span class="text-surface-600 dark:text-surface-300">Applications</span>
        <span :class="['font-medium tabular-nums', usageClass(limits.applicationCount, limits.maxApplicationsPerOrganization)]">
          {{ limits.applicationCount }} of {{ limits.maxApplicationsPerOrganization }}
        </span>
      </li>
      <template v-if="limits.maxMicroservicesPerApplication !== null">
        <li v-for="application in APPLICATION_STATE.allApplications" :key="application.id ?? ''"
            class="flex items-center justify-between gap-4">
          <span class="truncate text-surface-600 dark:text-surface-300">Microservices in {{ application.name }}</span>
          <span :class="['font-medium tabular-nums', usageClass(microservicesOf(application.id), limits.maxMicroservicesPerApplication)]">
            {{ microservicesOf(application.id) }} of {{ limits.maxMicroservicesPerApplication }}
          </span>
        </li>
      </template>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { APPLICATION_STATE } from '@/states/IApplicationState'

/**
 * The free beta cloud plan the organization is on: what it allows, and how much of each limit the
 * organization uses. Renders nothing when the server imposes no limits.
 */
const limits = computed(() => APPLICATION_STATE.betaLimits)

function microservicesOf(applicationId: string | null): number {
  return limits.value?.microservicesByApplication?.[applicationId ?? ''] ?? 0
}

function usageClass(used: number, max: number): string {
  return used >= max ? 'text-amber-700 dark:text-amber-300' : 'text-surface-950 dark:text-surface-0'
}
</script>
