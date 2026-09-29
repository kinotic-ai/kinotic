<template>
  <span
    :class="['inline-flex shrink-0 items-center justify-center font-sans font-semibold', SIZE_CLASSES[size], palette]"
    aria-hidden="true"
  >{{ initials }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TINTS } from '@/util/tints'

/**
 * A coloured tile carrying a name's initials, so neighbouring applications or projects are told
 * apart at a glance. Consecutive positions cycle through blue, green, orange, purple and red.
 */
const props = withDefaults(defineProps<{
  name: string
  /** The item's position in its list, which picks the colour. */
  index: number
  /** sm sits beside a table row's name; lg heads a card. */
  size?: 'sm' | 'lg'
}>(), {
  size: 'sm'
})

const SIZE_CLASSES = {
  sm: 'h-6 w-6 rounded-md text-[0.6875rem]',
  lg: 'h-12 w-12 rounded-xl text-lg'
}

const PALETTES = [TINTS.blue, TINTS.green, TINTS.orange, TINTS.purple, TINTS.red]

// One letter for a one-word name, the first letters of the first two words otherwise
const initials = computed(() => {
  const words = props.name.split(/[\s_-]+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0]?.[0] ?? '?')
  return letters.toUpperCase()
})

const palette = computed(() => PALETTES[props.index % PALETTES.length])
</script>
