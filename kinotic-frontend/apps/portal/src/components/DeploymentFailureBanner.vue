<template>
  <div :class="['flex items-start gap-3 rounded-xl border px-4 py-3.5', tone.box]" role="alert">
    <span :class="['flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', tone.tint]">
      <component :is="betaLimit ? Gauge : CircleAlert" :size="16" :stroke-width="1.75" aria-hidden="true" />
    </span>
    <div class="min-w-0 flex-1">
      <p :class="['text-sm font-semibold', tone.title]">{{ title }}</p>
      <p :class="['mt-0.5 break-words text-[0.8125rem]', tone.text]">{{ betaLimit ?? message }}</p>
    </div>
    <div v-if="$slots.action" :class="['flex shrink-0 items-center self-center whitespace-nowrap text-sm font-medium', tone.title]">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, Gauge } from '@lucide/vue'
import { TINTS } from '@kinotic-ai/frontend-common'
import { betaLimitMessage } from '@/util/betaLimit'

/**
 * Says why a project's last deployment did not go live: a free beta limit it reached, presented as the
 * plan's limit, or the failure it ran into. The `action` slot holds a link to where it can be looked into.
 */
const props = defineProps<{
  /** The deployment's `failureMessage`. */
  message: string
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

const betaLimit = computed(() => betaLimitMessage(props.message))
const tone = computed(() => betaLimit.value ? AMBER : RED)

const title = computed(() => {
  let ret: string
  if (betaLimit.value) {
    ret = `${props.projectName ?? 'This project'} reached a free beta limit, so its last push was not deployed`
  } else {
    ret = props.projectName ? `${props.projectName}'s last deployment failed` : 'The last deployment failed'
  }
  return ret
})
</script>
