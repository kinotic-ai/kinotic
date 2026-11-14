<template>
  <div class="flex flex-col">
    <span class="text-sm">{{ option.label }}</span>
    <span v-if="option.detail" class="text-xs text-muted-color">{{ option.detail }}</span>
  </div>
</template>

<script setup lang="ts">
import type { SubjectOption } from './SubjectOption'

/** A subject as a picker lists it: its name, and its detail beneath. */
defineProps<{
  option: SubjectOption
}>()
</script>
