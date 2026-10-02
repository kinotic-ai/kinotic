<template>
  <component
    :is="to ? RouterLink : href ? 'a' : 'div'"
    v-bind="to ? { to } : href ? { href, target: '_blank', rel: 'noopener' } : {}"
    :class="[
      'block rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-800/30',
      to || href ? 'group transition-colors hover:border-surface-300 hover:bg-surface-100 dark:hover:border-surface-600 dark:hover:bg-surface-800/70' : ''
    ]"
  >
    <div class="flex items-start justify-between">
      <span :class="['flex h-10 w-10 items-center justify-center rounded-lg', tint]">
        <slot name="icon">
          <component :is="icon" :size="20" :stroke-width="1.75" aria-hidden="true" />
        </slot>
      </span>
      <ArrowUpRight v-if="to || href" :size="16" :stroke-width="1.75"
                    class="text-surface-300 transition-colors group-hover:text-surface-600 dark:text-surface-600 dark:group-hover:text-surface-300" aria-hidden="true" />
    </div>
    <Skeleton v-if="loading" height="2.25rem" width="3.5rem" class="mt-4" />
    <div v-else-if="$slots.default" class="mt-4 flex h-9 items-center">
      <slot />
    </div>
    <div v-else class="mt-4 truncate text-3xl font-semibold tabular-nums tracking-tight text-surface-950 dark:text-surface-0"
         :title="value?.toString()">{{ value }}</div>
    <div class="mt-1 text-sm font-medium text-surface-800 dark:text-surface-100">{{ label }}</div>
    <div v-if="detail" :class="['mt-0.5 truncate text-xs text-muted-color', monoDetail ? 'font-mono' : '']">{{ detail }}</div>
  </component>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Skeleton from 'primevue/skeleton'
import { ArrowUpRight } from '@lucide/vue'

/**
 * One figure on an overview page: a tinted icon, the value (or the default slot, e.g. a status
 * tag), its label and a line of detail. Leads to {@code to} inside the portal or {@code href}
 * in a new tab when either is set.
 */
defineProps<{
  /** The tile's icon; the icon slot replaces it, e.g. with a HeartbeatIcon. */
  icon?: Component
  /** Classes of the icon tile's colour, one of TINTS. */
  tint: string
  label: string
  value?: number | string | null
  detail?: string
  /** Renders the detail in the monospace font, for ids and repository names. */
  monoDetail?: boolean
  loading?: boolean
  to?: RouteLocationRaw
  href?: string
}>()
</script>
