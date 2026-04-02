<template>
  <transition name="fade">
    <div
      v-if="visible"
      :class="['fixed inset-0 z-50 flex flex-col', isDark ? 'bg-[#171717] text-white' : 'bg-white text-[#101010]']"
    >
      <div :class="['flex justify-between items-center p-4 border-b', isDark ? 'border-[#2f2f35]' : 'border-gray-200']">
        <div class="flex justify-center items-center gap-3">
            <img src="@/assets/graphql-black.svg" />
            <h2 class="text-lg font-semibold">GraphQL Playground</h2>
        </div>
        <button @click="$emit('close')" :class="['text-xl', isDark ? 'text-[#9f9fa8] hover:text-white' : 'text-gray-500 hover:text-black']">×</button>
      </div>
      <div class="flex-1 overflow-hidden">
        <GraphQLPlayground />
      </div>
    </div>
  </transition>
</template>

<script lang="ts">
import { Component, Vue, Prop, Emit } from 'vue-facing-decorator'
import GraphQLPlayground from '@/pages/GraphQLPlayground.vue'
import { isDark as darkMode } from '@/composables/useTheme'

@Component({
  components: { GraphQLPlayground }
})
export default class GraphQLModal extends Vue {
  @Prop({ required: true }) readonly visible!: boolean
  get isDark() { return darkMode.value }
  @Emit('close') close(): void {}
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
