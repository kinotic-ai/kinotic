<script lang="ts">
import { Vue, Component, Ref } from "vue-facing-decorator"
import { VueFlow, type Node, type Edge } from "@vue-flow/core"
import { Background } from "@vue-flow/background"
import { Controls } from "@vue-flow/controls"
import { MiniMap } from "@vue-flow/minimap"
import { markRaw } from "vue"
import Button from "primevue/button"
import InputText from "primevue/inputtext"
import StructureNode from "@/components/structures/flow-components/StructureNode.vue"
import { useStructureStore } from "@/stores/editor"
import "@vue-flow/core/dist/style.css"
import "@vue-flow/core/dist/theme-default.css"
import "@vue-flow/minimap/dist/style.css"
import "@vue-flow/controls/dist/style.css"
import StructureSidebarDashboard from "@/components/structures/sidebar-dashboard/StructureSidebarDashboard.vue";

@Component({
  components: {
    StructureSidebarDashboard,
    StructureNode,
    Button,
    VueFlow,
    Background,
    Controls,
    MiniMap,
    InputText
  },
})
export default class NewStructure extends Vue {
  visible = true
  isEditing = false
  textWidth = 0
  textHeight = 0

  @Ref("nameTextEl") readonly nameTextEl!: HTMLElement | null

  // Store
  structureStore = useStructureStore();

  get name(): string {
    return this.structureStore.structure?.name ?? ""
  }
  set name(value: string) {
    this.structureStore.updateStructureName(value)
  }

  get flowNodes(): Node[] {
    return this.structureStore.nodes
  }
  get flowEdges(): Edge[] {
    return this.structureStore.edges
  }

  nodeTypes = {
    structure: markRaw(StructureNode),
  }

  beforeMount(): void {
    // Initialize structure once when modal opens
    this.structureStore.initNewStructure("app-123", "proj-456")
  }

  handleMouseEnter() {
    this.measureText()
    this.isEditing = true
  }
  handleMouseLeave() {
    this.isEditing = false
  }

  measureText() {
    if (this.nameTextEl) {
      const rect = this.nameTextEl.getBoundingClientRect()
      this.textWidth = rect.width
      this.textHeight = rect.height
    }
  }

  onHide() {
    this.visible = false
    this.$emit("close")
  }
}
</script>

<template>
  <div v-show="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-opacity-50">
    <div class="relative w-full h-screen bg-white shadow-lg overflow-hidden">
      <div class="flex h-full">
        <!-- Main Graph Area -->
        <div class="flex flex-col h-full" :style="{ width: `calc(100% - 320px)` }">
          <!-- Header -->
          <div class="p-2 bg-transparent fixed top-0 left-0 z-3" :style="{ width: `calc(100% - 320px)` }">
            <div class="flex items-center justify-between bg-white p-2 rounded-xl">
              <div class="flex items-center">
                <Button
                    severity="secondary"
                    variant="text"
                    icon="pi pi-arrow-left"
                    size="small"
                    class="mr-2"
                />
                <div
                    class="relative w-fit"
                    @mouseenter="handleMouseEnter"
                    @mouseleave="handleMouseLeave"
                >
                  <p
                      v-if="!isEditing"
                      ref="nameTextEl"
                      class="!font-semibold px-2 py-1 w-[140px] rounded border border-transparent cursor-pointer"
                  >
                    {{ name }}
                  </p>
                  <InputText
                      v-else
                      v-model="name"
                      size="small"
                      class="!font-semibold w-[160px] px-2 py-1 rounded border border-gray-300"
                  />
                </div>
              </div>
              <div class="flex gap-4">
                <Button icon="pi pi-replay" severity="secondary" size="small" aria-label="Undo" variant="text" />
                <Button icon="pi pi-refresh" severity="secondary" size="small" aria-label="Redo" variant="text" />
                <Button icon="pi pi-sitemap" severity="secondary" size="small" aria-label="Schema" variant="text" />
              </div>
            </div>
          </div>

          <!-- VueFlow Canvas -->
          <div class="flex-1 bg-surface-50">
            <VueFlow
                :nodeTypes="nodeTypes"
                :nodes="flowNodes"
                :edges="flowEdges"
                :minZoom="0.01"
            >
              <Background color="black" :gap="20" />
              <MiniMap />
              <Controls
                  position="bottom-left"
                  class="custom-flow-controls rotate-90 !rounded-2xl !ml-10 !bg-transparent"
                  :show-fit-view="false"
                  :show-interactive="false"
              />
            </VueFlow>
          </div>
        </div>

        <!-- Sidebar -->
         <StructureSidebarDashboard />
      </div>
    </div>
  </div>
</template>

<style scoped>
.p-row-even,
.p-row-odd {
  cursor: pointer !important;
}
.p-button-sm.p-button-icon-only {
  width: 44px;
}
</style>
