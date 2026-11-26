<template>
  <div v-if="notice" :class="['flex items-start gap-3 rounded-xl border px-4 py-3.5', notice.tone.box]" role="alert">
    <span :class="['flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', notice.tone.tint]">
      <component :is="notice.icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
    </span>
    <div class="min-w-0 flex-1">
      <p :class="['text-sm font-semibold', notice.tone.title]">{{ notice.title }}</p>
      <p :class="['mt-0.5 break-words text-[0.8125rem]', notice.tone.text]">{{ notice.message }}</p>
    </div>
    <div v-if="$slots.action" :class="['flex shrink-0 items-center self-center whitespace-nowrap text-sm font-medium', notice.tone.title]">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { CircleAlert, Gauge } from '@lucide/vue'
import { TINTS } from '@kinotic-ai/frontend-common'
import { DeploymentStatusType, type ProjectDeployment } from '@kinotic-ai/management-api'

/**
 * Says why a project's last deployment did not go live: a free beta limit it reached, explained as the
 * plan it is, or the failure it ran into. Renders nothing for a deployment that did neither. The
 * `action` slot holds a link to where it can be looked into.
 */
const props = defineProps<{
  deployment: ProjectDeployment | null
  /** Names the project in the title, for a page that lists several. */
  projectName?: string
}>()

const AMBER = {
  box: 'border-amber-200 bg-amber-50/70 dark:border-amber-500/30 dark:bg-amber-500/10',
  tint: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  title: 'text-amber-800 dark:text-amber-300',
  text: 'text-amber-700/90 dark:text-amber-300/90'
}
const RED = {
  box: 'border-red-200 bg-red-50/70 dark:border-red-500/30 dark:bg-red-500/10',
  tint: TINTS.red,
  title: 'text-red-800 dark:text-red-300',
  text: 'text-red-700/90 dark:text-red-300/90'
}

const notice = computed(() => {
  const deployment = props.deployment
  const subject = props.projectName ? `${props.projectName}` : 'This project'
  let ret = null
  if (deployment?.limitReached) {
    ret = { tone: AMBER, icon: markRaw(Gauge), message: deployment.limitReached,
            title: `${subject} reached a free beta limit, so its last push was not deployed` }
  } else if (deployment?.state.observed?.phase === DeploymentStatusType.FAILED && deployment.failureMessage) {
    ret = { tone: RED, icon: markRaw(CircleAlert), message: deployment.failureMessage,
            title: props.projectName ? `${props.projectName}'s last deployment failed` : 'The last deployment failed' }
  }
  return ret
})
</script>
