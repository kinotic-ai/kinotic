<script lang="ts" setup>
defineProps<{ title: string; preview: string; draggable?: boolean }>()
</script>

<template>
  <div
    class="border border-[#E6E7EB] rounded-lg p-4 bg-white relative select-none"
  >
    <h3 class="text-sm font-semibold mb-2 text-surface-800">{{ title }}</h3>
    <div class="text-surface-500 text-sm min-h-[40px]">{{ preview }}</div>
  </div>
</template>

<style scoped>
</style>


