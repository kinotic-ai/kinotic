<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator'
import StructureSettings from './StructureSettings.vue'

@Component({
  components: {
    StructureSettings
  }
})
export default class SidebarSettings extends Vue {}
</script>

<template>
  <div class="w-[320px] h-full border border-surface-200 bg-white">
    <StructureSettings />
    <!-- More types will be added here later -->
  </div>
</template>
