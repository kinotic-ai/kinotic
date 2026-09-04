<template>
  <Dialog
    :visible="true"
    modal
    :draggable="false"
    :dismissable-mask="false"
    class="p-dialog-maximized"
    content-class="flex min-h-0 flex-1 flex-col"
    @update:visible="close"
  >
    <template #header>
      <div class="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-4">
        <div class="min-w-0">
          <div class="mb-1 flex items-center gap-1.5 text-sm text-surface-500 dark:text-surface-400">
            <RouterLink :to="entitiesPath" class="hover:underline">Entities</RouterLink>
            <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
            <span>{{ entity?.name ?? entityDefinitionId }}</span>
          </div>
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="m-0 text-xl font-semibold tracking-tight text-surface-950 dark:text-surface-0">{{ entity?.name ?? entityDefinitionId }}</h1>
            <Tag v-if="entity" :value="entity.published ? 'Published' : 'Unpublished'"
                 :severity="entity.published ? 'success' : 'secondary'" rounded />
          </div>
          <p v-if="entity?.description" class="m-0 mt-1 max-w-[640px] text-sm text-surface-500 dark:text-surface-400">{{ entity.description }}</p>
        </div>
        <div v-if="entity" class="flex shrink-0 items-center gap-2">
          <Button v-if="entity.published" label="Unpublish" icon="pi pi-eye-slash" severity="danger" outlined
                  :loading="busyId === entity.id" @click="unpublish(entity)" />
          <Button v-else label="Publish" icon="pi pi-eye"
                  :loading="busyId === entity.id" @click="publish(entity)" />
        </div>
      </div>
    </template>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading entity…</div>

    <Tabs v-else-if="entity" lazy :value="activeTab" class="flex min-h-0 flex-1 flex-col" @update:value="selectTab">
      <TabList>
        <Tab value="data">
          <span class="flex items-center gap-2"><i class="pi pi-table" />Data</span>
        </Tab>
        <Tab value="schema">
          <span class="flex items-center gap-2"><i class="pi pi-sitemap" />Schema</span>
        </Tab>
      </TabList>
      <TabPanels class="flex min-h-0 flex-1 flex-col">
        <TabPanel value="data" class="flex min-h-0 flex-1 flex-col">
          <EntityList v-if="entity.published" :key="entity.id ?? ''" :entity-definition-id="entity.id ?? undefined" />
          <div v-else class="rounded-2xl border border-dashed border-surface-300 p-8 text-center text-sm text-muted-color dark:border-surface-700">
            Publish this entity to start storing data. Its schema is ready on the Schema tab.
          </div>
        </TabPanel>
        <TabPanel value="schema" class="flex min-h-0 flex-1 flex-col">
          <EntityDefinitionDiagram :key="entity.id ?? ''" :entity="entity" />
        </TabPanel>
      </TabPanels>
    </Tabs>

    <ConfirmDialog />
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
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
import EntityDefinitionDiagram from '@/components/entity-definitions/EntityDefinitionDiagram.vue'
import EntityList from '@/pages/EntityList.vue'
import { useEntityPublishing } from '@/composables/entity-definition/useEntityPublishing'
import { useQueryTab } from '@/composables/useQueryTab'

/**
 * One entity definition: the data stored under it and its schema, with publishing as the
 * action that turns the schema into a store. It has its own route under the Entities list it
 * was opened from, and fills the viewport as a dialog over that list, since both the data table
 * and the schema diagram need the room; closing it returns to the list. The dialog wears the
 * theme's own maximized class, the one its maximize button would apply.
 */
const props = defineProps<{
  applicationId: string
  projectId?: string
  entityDefinitionId: string
}>()

const TABS = ['data', 'schema'] as const

const router = useRouter()

const entity = ref<EntityDefinition | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const activeTab = useQueryTab(TABS)
const { busyId, publish, unpublish } = useEntityPublishing(load)

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
  activeTab.value = value === 'schema' ? 'schema' : 'data'
}

function close(): void {
  router.push(entitiesPath.value)
}
</script>
