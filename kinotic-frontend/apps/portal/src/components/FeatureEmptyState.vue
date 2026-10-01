<template>
  <section class="grid items-center gap-10 py-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:py-12">
    <div class="feature-canvas relative mx-auto w-full max-w-[520px] rounded-2xl border border-surface-200 px-8 pb-10 pt-12 dark:border-surface-700">
      <span :class="['absolute -top-5 left-8 flex h-12 w-12 items-center justify-center rounded-xl shadow-sm ring-4 ring-surface-0 dark:ring-surface-900', tint]">
        <component :is="icon" :size="22" :stroke-width="1.75" aria-hidden="true" />
      </span>
      <!-- A faded preview of the screen to come; decorative, so it is hidden from assistive technology -->
      <div class="feature-preview pointer-events-none select-none" aria-hidden="true">
        <slot name="preview" />
      </div>
    </div>

    <div class="max-w-[460px]">
      <span :class="['inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium', BADGES[badge].classes]">
        <component :is="BADGES[badge].icon" :size="12" :stroke-width="2" aria-hidden="true" />
        {{ BADGES[badge].label }}
      </span>
      <h2 class="mt-4 text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0">{{ title }}</h2>
      <p class="mt-2 text-sm leading-6 text-muted-color">{{ description }}</p>

      <ul class="mt-6 flex flex-col gap-3">
        <li v-for="point in points" :key="point" class="flex items-start gap-3 text-sm text-surface-800 dark:text-surface-100">
          <span class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300">
            <Check :size="12" :stroke-width="2.5" aria-hidden="true" />
          </span>
          {{ point }}
        </li>
      </ul>

      <div v-if="$slots.actions" class="mt-8 flex flex-wrap items-center gap-3">
        <slot name="actions" />
      </div>

      <div v-else-if="meanwhile" class="mt-8 flex items-center gap-2 border-t border-surface-200 pt-5 text-sm dark:border-surface-700">
        <span class="text-muted-color">Meanwhile,</span>
        <a v-if="meanwhile.href" :href="meanwhile.href" target="_blank" rel="noopener"
           class="inline-flex items-center gap-1 font-medium text-surface-950 hover:underline dark:text-surface-0">
          {{ meanwhile.label }}
          <ArrowUpRight :size="14" :stroke-width="1.75" aria-hidden="true" />
        </a>
        <RouterLink v-else :to="meanwhile.to ?? ''"
                    class="inline-flex items-center gap-1 font-medium text-surface-950 hover:underline dark:text-surface-0">
          {{ meanwhile.label }}
          <ArrowRight :size="14" :stroke-width="1.75" aria-hidden="true" />
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { markRaw, type Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { ArrowRight, ArrowUpRight, Check, MailCheck, Sparkles, Unplug } from '@lucide/vue'

/** Why the feature shows its empty state: not built yet, not set up yet, or running on its built-in default. */
export type FeatureEmptyStateBadge = 'coming-soon' | 'not-connected' | 'built-in'

/**
 * The empty state of a feature that can't be used yet: a faded preview of its screen, given in
 * the preview slot, beside what the feature does and either the actions that set it up (the
 * actions slot) or what can be done in the meantime.
 */
defineProps<{
  badge: FeatureEmptyStateBadge
  icon: Component
  /** Classes of the icon tile's colour, one of TINTS. */
  tint: string
  title: string
  description: string
  /** What the feature will let people do, one line each. */
  points: string[]
  /** Something that works today, inside the portal (to) or in a new tab (href); shown when there are no actions. */
  meanwhile?: { label: string, to?: RouteLocationRaw, href?: string }
}>()

const BADGES: Record<FeatureEmptyStateBadge, { label: string, icon: Component, classes: string }> = {
  'coming-soon': {
    label: 'Coming soon',
    icon: markRaw(Sparkles),
    classes: 'border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-300'
  },
  'built-in': {
    label: 'Using the built-in email',
    icon: markRaw(MailCheck),
    classes: 'border-green-200 bg-green-50 text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-300'
  },
  'not-connected': {
    label: 'Not connected',
    icon: markRaw(Unplug),
    classes: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300'
  }
}
</script>

<style scoped>
.feature-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-400) 16%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, var(--p-sky-50), color-mix(in srgb, var(--p-indigo-50) 60%, transparent), color-mix(in srgb, var(--p-violet-50) 70%, transparent));
  background-size: 14px 14px, 100% 100%;
}

.dark .feature-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-500) 14%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, color-mix(in srgb, var(--p-sky-500) 10%, transparent), color-mix(in srgb, var(--p-indigo-500) 5%, transparent), color-mix(in srgb, var(--p-violet-500) 10%, transparent));
}

/* the preview fades out toward the bottom so it reads as a glimpse, not a working screen */
.feature-preview {
  -webkit-mask-image: linear-gradient(to bottom, black 55%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 55%, transparent 100%);
}
</style>
