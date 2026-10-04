<template>
  <div class="flex flex-col items-center rounded-2xl border border-dashed border-surface-300 px-6 py-12 text-center dark:border-surface-700">
    <span :class="['flex h-12 w-12 items-center justify-center rounded-xl', TINTS.ink]">
      <Lock :size="22" :stroke-width="1.75" aria-hidden="true" />
    </span>
    <h2 class="mt-4 text-lg font-semibold text-surface-950 dark:text-surface-0">{{ title }}</h2>
    <p class="mt-1 max-w-[460px] text-sm leading-6 text-muted-color">{{ description }}</p>
    <RouterLink v-if="backTo" :to="backTo" class="mt-6 inline-flex items-center gap-1 text-sm font-medium text-surface-950 hover:underline dark:text-surface-0">
      <ArrowLeft :size="14" :stroke-width="1.75" aria-hidden="true" />
      {{ backLabel }}
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { ArrowLeft, Lock } from '@lucide/vue'
import { TINTS } from '../util/tints'

/**
 * What a page shows in place of something the gateway refused: the signed-in member or operator holds no grant
 * that reaches it. An administrator grants access from the thing's Access page.
 */
withDefaults(defineProps<{
  title?: string
  description?: string
  /** Where to go instead, such as the list the page was opened from. */
  backTo?: RouteLocationRaw
  backLabel?: string
}>(), {
  title: 'You don\'t have access',
  description: 'No grant you hold reaches this. An administrator can grant you access from its Access page.',
  backLabel: 'Go back'
})
</script>
