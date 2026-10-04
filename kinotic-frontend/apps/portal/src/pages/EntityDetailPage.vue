<template>
  <Dialog
    :visible="true"
    modal
    :draggable="false"
    :dismissable-mask="false"
    class="p-dialog-maximized"
    :pt="{ header: { class: '!py-3' } }"
    content-class="flex min-h-0 flex-1 flex-col"
    @update:visible="close"
  >
    <template #header>
      <div class="flex min-w-0 flex-1 items-center gap-2 text-sm">
        <RouterLink :to="entitiesPath" class="text-surface-500 hover:underline dark:text-surface-400">Entities</RouterLink>
        <i class="pi pi-chevron-right text-surface-500 dark:text-surface-400" :style="{ fontSize: '10px' }" />
        <span class="truncate font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="entity?.description || undefined">{{ entity?.name ?? entityDefinitionId }}</span>
        <Tag v-if="entity" :value="entity.published ? 'Published' : 'Unpublished'"
             :severity="entity.published ? 'success' : 'secondary'" rounded />
      </div>
    </template>
    <!-- Rendered here rather than by the dialog, which would move focus to its own close
         button on open and draw the button's focus ring around it -->
    <template #closebutton="{ closeCallback }">
      <Button icon="pi pi-times" severity="secondary" text rounded aria-label="Close" @click="closeCallback" />
    </template>

    <NoAccessState v-if="error && isAuthorizationError(error)" class="m-6" :back-to="entitiesPath" back-label="Back to entities" />
    <Message v-else-if="error" severity="error" :closable="false">{{ error }}</Message>
    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading entity…</div>

    <Tabs v-else-if="entity" lazy :value="activeTab" class="flex min-h-0 flex-1 flex-col" @update:value="selectTab">
      <TabList>
        <Tab value="data">
          <span class="flex items-center gap-2"><Table :size="18" :stroke-width="1.75" aria-hidden="true" />Data</span>
        </Tab>
        <Tab value="schema">
          <span class="flex items-center gap-2"><Braces :size="18" :stroke-width="1.75" aria-hidden="true" />Schema</span>
        </Tab>
        <Tab value="access">
          <span class="flex items-center gap-2"><KeyRound :size="18" :stroke-width="1.75" aria-hidden="true" />Access</span>
        </Tab>
      </TabList>
      <TabPanels class="flex min-h-0 flex-1 flex-col">
        <TabPanel value="data" class="flex min-h-0 flex-1 flex-col">
          <EntityList v-if="entity.published" :key="entity.id ?? ''" :entity-definition-id="entity.id ?? undefined" />
          <div v-else class="rounded-2xl border border-dashed border-surface-300 p-8 text-center text-sm text-muted-color dark:border-surface-700">
            Publish this entity from the Entities list to start storing data. Its schema is ready on the Schema tab.
          </div>
        </TabPanel>
        <TabPanel value="schema" class="flex min-h-0 flex-1 flex-col">
          <EntityDefinitionDiagram :key="entity.id ?? ''" :entity="entity" />
        </TabPanel>
        <TabPanel value="access" class="min-h-0 flex-1 overflow-y-auto pt-4">
          <AccessPanel :key="entity.id ?? ''" :resource="{ type: 'entity_definition', id: entity.id ?? '' }" :tint="TINTS.purple"
                       :application-id="entity.applicationId" :project-id="entity.projectId" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </Dialog>
</template>

<script setup lang="ts">
import { Braces, KeyRound, Table } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { Kinotic } from '@kinotic-ai/core'
import type { EntityDefinition } from '@kinotic-ai/management-api'
import { NoAccessState, TINTS, isAuthorizationError } from '@kinotic-ai/frontend-common'
import AccessPanel from '@/components/access/AccessPanel.vue'
import EntityDefinitionDiagram from '@/components/entity-definitions/EntityDefinitionDiagram.vue'
import EntityList from '@/pages/EntityList.vue'
import { useQueryTab } from '@/composables/useQueryTab'

/**
 * One entity definition: the data stored under it, its schema and its access. It has its own route under
 * the Entities list it was opened from, and fills the viewport as a dialog over that list,
 * since both the data table and the schema diagram need the room; the header is one row so
 * they get as much of it as possible, and closing it returns to the list. Publishing stays on
 * the list. The dialog wears the theme's own maximized class, the one its maximize button
 * would apply.
 */
const props = defineProps<{
  applicationId: string
  projectId?: string
  entityDefinitionId: string
}>()

const TABS = ['data', 'schema', 'access'] as const

const router = useRouter()

const entity = ref<EntityDefinition | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const activeTab = useQueryTab(TABS)

const entitiesPath = computed(() => {
  const applicationPath = `/application/${encodeURIComponent(props.applicationId)}`
  return props.projectId
      ? `${applicationPath}/project/${encodeURIComponent(props.projectId)}/entities`
      : `${applicationPath}/entities`
})

watch(() => props.entityDefinitionId, load, { immediate: true })

async function load(): Promise<void> {
  loading.value = entity.value === null
  error.value = null
  try {
    entity.value = await Kinotic.entityDefinitions.findById(props.entityDefinitionId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function selectTab(value: string | number): void {
  activeTab.value = TABS.find(tab => tab === value) ?? 'data'
}

function close(): void {
  router.push(entitiesPath.value)
}
</script>
