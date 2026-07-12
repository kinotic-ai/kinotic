<script setup lang="ts">
import { VueFlow, type Node, type Edge, type NodeTypesObject } from "@vue-flow/core"
import { Background } from "@vue-flow/background"
import { Controls } from "@vue-flow/controls"
import { MiniMap } from "@vue-flow/minimap"
import { computed, markRaw, onBeforeMount, ref } from "vue"
import Button from "primevue/button"
import InputText from "primevue/inputtext"
import EntityDefinitionNode from "@/components/entity-definitions/flow-components/EntityDefinitionNode.vue"
import { useEntityDefinitionStore } from "@/stores/editor"
import { USER_STATE } from "@/states/IUserState"
import "@vue-flow/core/dist/style.css"
import "@vue-flow/core/dist/theme-default.css"
import "@vue-flow/minimap/dist/style.css"
import "@vue-flow/controls/dist/style.css"
import EntityDefinitionSidebarDashboard from "@/components/entity-definitions/sidebar-dashboard/EntityDefinitionSidebarDashboard.vue";
import { isDark as darkMode } from '@/composables/useTheme'

const visible = ref(true)
const isEditing = ref(false)
const textWidth = ref(0)
const textHeight = ref(0)

const nameTextEl = ref<HTMLElement | null>(null)

// Store
const entityDefinitionStore = useEntityDefinitionStore();

const name = computed<string>({
  get: () => entityDefinitionStore.entityDefinition?.name ?? "",
  set: (value) => {
    entityDefinitionStore.updateEntityDefinitionName(value)
  },
})

const flowNodes = computed<Node[]>(() => entityDefinitionStore.nodes)
const flowEdges = computed<Edge[]>(() => entityDefinitionStore.edges)

// Cast: VueFlow's NodeTypesObject wants NodeComponent values, which SFC-typed
// components don't structurally satisfy even though they work at runtime.
const nodeTypes = {
  entityDefinition: markRaw(EntityDefinitionNode),
} as unknown as NodeTypesObject

onBeforeMount(() => {
  // Initialize entityDefinition once when modal opens
  entityDefinitionStore.initNewEntityDefinition(USER_STATE.getOrganizationId(), "app-123", "proj-456")
})

function handleMouseEnter() {
  measureText()
  isEditing.value = true
}
function handleMouseLeave() {
  isEditing.value = false
}

function measureText() {
  if (nameTextEl.value) {
    const rect = nameTextEl.value.getBoundingClientRect()
    textWidth.value = rect.width
    textHeight.value = rect.height
  }
}

const isDark = darkMode
</script>

<template>
  <div v-show="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-opacity-50">
    <div :class="['relative w-full h-screen shadow-lg overflow-hidden', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
      <div class="flex h-full">
        <!-- Main Graph Area -->
        <div class="flex flex-col h-full" :style="{ width: `calc(100% - 320px)` }">
          <!-- Header -->
          <div class="p-2 bg-transparent fixed top-0 left-0 z-3" :style="{ width: `calc(100% - 320px)` }">
            <div :class="['flex items-center justify-between p-2 rounded-xl', isDark ? 'border border-surface-800 bg-surface-900' : 'bg-surface-0']">
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
                      :class="isDark ? '!font-semibold w-[160px] px-2 py-1 rounded border !border-surface-700 !bg-surface-800 !text-surface-0' : '!font-semibold w-[160px] px-2 py-1 rounded border border-gray-300'"
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
          <div :class="['flex-1', isDark ? 'bg-surface-900' : 'bg-surface-50']">
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
         <EntityDefinitionSidebarDashboard />
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
