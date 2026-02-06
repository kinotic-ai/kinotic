<template>
  <div class="flex flex-col items-center justify-center text-center py-12 text-gray-400">
    <slot name="icon">
      <i class="pi pi-database text-3xl mb-3"></i>
    </slot>
    <p class="text-sm">{{ message }}</p>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'

@Component
export default class EmptyState extends Vue {
  @Prop({ required: true }) message!: string
}
</script>
