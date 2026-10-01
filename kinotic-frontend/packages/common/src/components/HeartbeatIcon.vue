<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="strokeWidth"
       stroke-linecap="round" stroke-linejoin="round" class="heartbeat" aria-hidden="true">
    <template v-if="state === HeartbeatState.ALIVE">
      <path class="heartbeat__trace" :d="TRACE" pathLength="100" />
      <path class="heartbeat__pulse" :d="TRACE" pathLength="100" />
    </template>
    <path v-else-if="state === HeartbeatState.FAILED" :d="LAST_BEAT" />
    <path v-else d="M2 12h20" />
  </svg>
</template>

<script setup lang="ts">
import { HeartbeatState } from './HeartbeatState'

/**
 * An EKG trace showing whether something is alive: while alive, a pulse sweeps along the trace
 * the way a heart monitor draws a beat; once failed, one last beat drops to a flat line; idle,
 * the line lies flat. Takes the same size and strokeWidth as a Lucide icon, and draws in the
 * current text color.
 */
withDefaults(defineProps<{
  state: HeartbeatState
  size?: number
  strokeWidth?: number
}>(), {
  size: 24,
  strokeWidth: 2
})

// A flat run-in, a small P wave, the QRS spike and a T wave before the flat run-out
const TRACE = 'M2 12h4l1.5-2 1.5 2h1l1.5-7 2 13 1.5-6h1.5l1.5-2 1.5 2H22'
// One beat, then nothing: the trace runs flat after the spike
const LAST_BEAT = 'M2 12h5l1.5-6 2 11 1.5-5H22'
</script>

<style scoped>
.heartbeat__trace {
  opacity: 0.3;
}

/* pathLength is 100, so the 30-unit dash is the pulse and the offset walks it off the trace */
.heartbeat__pulse {
  stroke-dasharray: 30 100;
  stroke-dashoffset: 30;
  animation: heartbeat-sweep 1.8s linear infinite;
}

@keyframes heartbeat-sweep {
  0% {
    stroke-dashoffset: 30;
  }
  /* the sweep takes 60% of the cycle; the rest is the pause between beats */
  60%,
  100% {
    stroke-dashoffset: -100;
  }
}

@media (prefers-reduced-motion: reduce) {
  .heartbeat__trace {
    opacity: 1;
  }

  .heartbeat__pulse {
    display: none;
  }
}
</style>
