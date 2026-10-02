<template>
  <div class="pipeline-canvas overflow-x-auto rounded-xl border border-sky-100 px-8 pb-6 pt-12 dark:border-sky-400/10">
    <!-- labels hang below their tiles, so connectors run edge to edge between the tiles themselves -->
    <div class="flex min-w-max items-center pb-16">
      <div class="relative shrink-0" :style="stepColor(DONE_COLOR)">
        <div class="tile tile--flag relative z-[1] flex h-11 w-11 items-center justify-center rounded-xl">
          <Flag :size="20" :stroke-width="2" class="fill-current" aria-hidden="true" />
        </div>
        <span class="label-eyebrow absolute left-1/2 top-full mt-3 -translate-x-1/2">Start</span>
      </div>

      <template v-for="(task, index) in tasks" :key="task.taskPath">
        <div class="flex h-2.5 min-w-[6rem] flex-1 items-center" :style="connectorStyle(index)">
          <span class="line h-1 flex-1" />
        </div>

        <div class="relative shrink-0" :style="stepColor(task.status === ExecutionStatus.COMPLETED ? DONE_COLOR : colorOf(index))">
          <!-- a failed step opens its row in the ledger, where its log and error are -->
          <component :is="task.status === ExecutionStatus.FAILED ? 'button' : 'div'"
                     v-bind="task.status === ExecutionStatus.FAILED ? { type: 'button', 'aria-label': `Show why ${task.description} failed` } : {}"
                     :class="['tile relative z-[1] flex h-11 w-11 items-center justify-center rounded-xl',
                              `tile--${task.status.toLowerCase()}`,
                              task.status === ExecutionStatus.FAILED ? 'cursor-pointer transition-transform hover:scale-105' : '']"
                     @click="task.status === ExecutionStatus.FAILED && emit('select', task)">
            <component :is="taskIcon?.(task) ?? Workflow" :size="20" :stroke-width="1.75" aria-hidden="true" />
            <span v-if="TASK_STATUS_STYLE[task.status].badge"
                  class="absolute -top-3.5 left-1/2 flex h-[1.125rem] w-[1.125rem] -translate-x-1/2 items-center justify-center rounded-full ring-2 ring-surface-0 dark:ring-surface-900"
                  :class="TASK_STATUS_STYLE[task.status].badge">
              <component :is="TASK_STATUS_STYLE[task.status].icon" :size="11" :stroke-width="3"
                         :class="task.status === ExecutionStatus.RUNNING ? 'animate-spin' : ''" aria-hidden="true" />
            </span>
          </component>
          <div class="absolute left-1/2 top-full mt-3 w-32 -translate-x-1/2 text-center">
            <div class="label-eyebrow">Step {{ index + 1 }}</div>
            <div class="mt-0.5 line-clamp-2 text-sm font-medium leading-5"
                 :class="task.status === ExecutionStatus.PENDING ? 'text-surface-400 dark:text-surface-500' : 'text-surface-950 dark:text-surface-0'"
                 v-tooltip.top="task.description">{{ task.description || `Task ${task.sequence}` }}</div>
          </div>
        </div>
      </template>

      <div class="flex h-2.5 min-w-[6rem] flex-1 items-center" :style="connectorStyle(tasks.length)">
        <span class="line h-1 flex-1" />
      </div>
      <div class="relative shrink-0" :style="stepColor(DONE_COLOR)">
        <div :class="['tile relative z-[1] flex h-11 w-11 items-center justify-center rounded-xl', finished ? 'tile--flag' : 'tile--pending']">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M5 21V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <rect x="5.75" y="3.75" width="14.5" height="10" rx="1" stroke="currentColor" stroke-width="1.5" />
            <path fill="currentColor"
                  d="M6 4h3.5v3.33H6zM13 4h3.5v3.33H13zM9.5 7.33H13v3.33H9.5zM16.5 7.33H20v3.33h-3.5zM6 10.67h3.5V14H6zM13 10.67h3.5V14H13z" />
          </svg>
        </div>
        <span class="label-eyebrow absolute left-1/2 top-full mt-3 -translate-x-1/2">Finish</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Flag, Workflow } from '@lucide/vue'
import { ExecutionStatus } from '@kinotic-ai/management-api'
import type { JobTaskNode } from './JobTaskNode'
import { TASK_STATUS_STYLE } from './jobRunDisplay'

/**
 * The run's top-level tasks as a pipeline on a dotted canvas, from a start flag to a finish
 * flag: one tile per task with its icon and its status badge, in a color of its own until the
 * task completes and green from then on, joined by connectors that turn green as the run
 * gets past each task.
 */
const props = defineProps<{
  tasks: JobTaskNode[]
  /** The icon of a task's tile, undefined for the default. */
  taskIcon?: (node: JobTaskNode) => Component | undefined
}>()

const emit = defineEmits<{
  /** A failed step's tile was clicked. */
  (e: 'select', task: JobTaskNode): void
}>()

/** The color of a completed step and of the flags. */
const DONE_COLOR = 'emerald'
const STEP_COLORS = ['sky', 'violet', 'amber', 'pink', 'indigo', 'teal']

const finished = computed(() => props.tasks.every(task => task.status === ExecutionStatus.COMPLETED))

function colorOf(index: number): string {
  return STEP_COLORS[index % STEP_COLORS.length]
}

/** The CSS variables a tile and its connectors read their palette from. */
function stepColor(color: string): Record<string, string> {
  return {
    '--step-300': `var(--p-${color}-300)`,
    '--step-400': `var(--p-${color}-400)`,
    '--step-500': `var(--p-${color}-500)`,
    '--step-600': `var(--p-${color}-600)`
  }
}

/**
 * The fill of the connector entering the task at {@code index} (the finish flag at tasks.length),
 * set by the status of what it leaves: green once that is done.
 */
function connectorStyle(index: number): Record<string, string> {
  const previous = index > 0 ? props.tasks[index - 1].status : ExecutionStatus.COMPLETED
  let ret: string
  if (previous === ExecutionStatus.COMPLETED) {
    ret = 'var(--done-line)'
  } else if (previous === ExecutionStatus.FAILED) {
    ret = 'var(--p-red-400)'
  } else if (previous === ExecutionStatus.RUNNING) {
    ret = 'linear-gradient(90deg, var(--done-line), var(--idle-line))'
  } else {
    ret = 'var(--idle-line)'
  }
  return { '--line': ret }
}
</script>

<style scoped>
.pipeline-canvas {
  --idle-line: var(--p-surface-300);
  --done-line: var(--p-emerald-500);
  /* the canvas-colored gap between a tile and its outline; connectors end at the outline */
  --ring-gap: var(--p-surface-0);
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-400) 16%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, var(--p-sky-50), color-mix(in srgb, var(--p-indigo-50) 60%, transparent), color-mix(in srgb, var(--p-violet-50) 70%, transparent));
  background-size: 14px 14px, 100% 100%;
}

.dark .pipeline-canvas {
  --idle-line: var(--p-surface-600);
  --done-line: var(--p-emerald-400);
  --ring-gap: var(--p-surface-900);
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-500) 14%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, color-mix(in srgb, var(--p-sky-500) 10%, transparent), color-mix(in srgb, var(--p-indigo-500) 5%, transparent), color-mix(in srgb, var(--p-violet-500) 10%, transparent));
}

.line {
  background: var(--line);
}

.label-eyebrow {
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--p-surface-500);
}

.dark .label-eyebrow {
  color: var(--p-surface-400);
}


.tile--completed,
.tile--flag {
  color: var(--step-600);
  background: color-mix(in srgb, var(--step-500) 14%, var(--p-surface-0));
  border: 1px solid color-mix(in srgb, var(--step-500) 30%, transparent);
  box-shadow: 0 0 0 4px var(--ring-gap), 0 0 0 6px var(--done-line);
}

.tile--running {
  color: white;
  background: var(--step-500);
  box-shadow: 0 0 0 4px var(--ring-gap), 0 0 0 6px var(--step-500), 0 4px 18px color-mix(in srgb, var(--step-500) 45%, transparent);
}

.tile--pending {
  color: var(--p-surface-400);
  background: color-mix(in srgb, var(--p-surface-0) 60%, transparent);
  border: 1px dashed var(--p-surface-300);
  box-shadow: 0 0 0 4px var(--ring-gap);
}

.tile--failed {
  color: var(--p-red-600);
  background: color-mix(in srgb, var(--p-red-500) 12%, var(--p-surface-0));
  border: 1px solid var(--p-red-300);
  box-shadow: 0 0 0 4px var(--ring-gap), 0 0 0 6px var(--p-red-400);
}

.tile--cancelled {
  color: var(--p-amber-600);
  background: color-mix(in srgb, var(--p-amber-500) 12%, var(--p-surface-0));
  border: 1px solid var(--p-amber-300);
  box-shadow: 0 0 0 4px var(--ring-gap), 0 0 0 6px var(--p-amber-400);
}


.dark .tile--completed,
.dark .tile--flag {
  color: var(--step-300);
  background: color-mix(in srgb, var(--step-500) 18%, var(--p-surface-900));
}

.dark .tile--pending {
  color: var(--p-surface-500);
  background: color-mix(in srgb, var(--p-surface-900) 50%, transparent);
  border-color: var(--p-surface-600);
}

.dark .tile--failed {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 18%, var(--p-surface-900));
}

.dark .tile--cancelled {
  color: var(--p-amber-300);
  background: color-mix(in srgb, var(--p-amber-500) 18%, var(--p-surface-900));
}
</style>
