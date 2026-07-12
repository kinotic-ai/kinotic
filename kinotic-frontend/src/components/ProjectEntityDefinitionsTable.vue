<script setup lang="ts">
import { ref, computed, watch, onMounted, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CrudTable from '@/components/CrudTable.vue'
import EntityDefinitionItemModal from '@/components/modals/EntityDefinitionItemModal.vue'
import EntityDataViewModal from '@/components/modals/EntityDataViewModal.vue'
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import { Kinotic } from '@kinotic-ai/core'
import { EntityDefinition } from '@kinotic-ai/os-api'
import type { CrudHeader } from '@/types/CrudHeader'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import type { Identifiable, IterablePage, Pageable } from '@kinotic-ai/core'
import DatetimeUtil from "@/util/DatetimeUtil"
import { createDebug } from '@/util/debug'

const debug = createDebug('project-entity-definitions-table');

const props = withDefaults(defineProps<{
  // Optional: ProjectList renders this table without an applicationId and the
  // table falls back to route params for its project scope.
  applicationId?: string
  showNewEntityDefinitionButton?: boolean
  newEntityDefinitionButtonText?: string
}>(), {
  showNewEntityDefinitionButton: false,
  newEntityDefinitionButtonText: "New Entity",
})

const route = useRoute()
const router = useRouter()
const instance = getCurrentInstance()

const crudTable = ref<InstanceType<typeof CrudTable>>()

const projectId = computed<string>(() => route.params.projectId as string)

const entityDefinitionTableHeaders: CrudHeader[] = [
  { field: 'name', header: 'Entity Name', sortable: true },
  { field: 'description', header: 'Description', sortable: false },
  { field: 'created', header: 'Created', sortable: false },
  { field: 'updated', header: 'Updated', sortable: false },
  { field: 'published', header: 'Status', sortable: false, centered: true }
]

const searchText = ref<string>('')
const showModal = ref(false)
const showItemModal = ref(false)
const showPublishModal = ref(false)
const showUnpublishModal = ref(false)
const selectedEntityDefinition = ref<EntityDefinition | null>(null)
const isInitialized = ref(false)
const actionMenus = ref<any[]>([])
const currentActionItem = ref<EntityDefinition | null>(null)
const dataSource1 = Kinotic.entityDefinitions

onMounted(() => {
  searchText.value = (route.query['search-entityDefinition'] as string) || ''
  isInitialized.value = true
  refreshTable()
  markProjectAsActive()
})

watch(() => route.query['search-entityDefinition'] as string, (val) => {
  if (isInitialized.value) {
    searchText.value = val || ''
    refreshTable()
  }
})

watch(() => route.params.projectId, () => {
  refreshTable()
  markProjectAsActive()
}, { immediate: true })

watch(() => props.applicationId, (newApplicationId, oldApplicationId) => {
  debug('applicationId changed from %s to %s', oldApplicationId, newApplicationId)
  refreshTable()
  markProjectAsActive()
}, { immediate: true })

const isProjectEntityDefinitionsPage = computed<boolean>(() => {
  return /^\/application\/[^/]+\/project\/[^/]+\/entity-definitions$/.test(route.path)
})

watch(() => APPLICATION_STATE.currentApplication, async (newApp: any, oldApp: any) => {
  debug('APPLICATION_STATE.currentApplication changed from %s to %s', oldApp?.id, newApp?.id)

  if (isProjectEntityDefinitionsPage.value && newApp && newApp.id !== oldApp?.id) {
    await handleApplicationChangeForProjectEntityDefinitions(newApp)
  } else {
    refreshTable()
    markProjectAsActive()
  }
}, { immediate: true })

async function handleApplicationChangeForProjectEntityDefinitions(newApp: any) {
  try {
    debug('Handling application change for ProjectEntityDefinitions page')

    const pageable = { pageNumber: 0, pageSize: 1 } as any
    const result = await Kinotic.projects.findAllForApplication(newApp.id, pageable)
    const firstProject = result.content?.[0]

    if (firstProject) {
      debug('Found first project: %s, navigating to it', firstProject.name)

      const applicationId = newApp.id
      const projectId = firstProject.id ?? ''
      await router.push(`/application/${encodeURIComponent(applicationId)}/project/${encodeURIComponent(projectId)}/entity-definitions`)
    } else {
      debug('No projects found for application: %s', newApp.id)
      refreshTable()
      markProjectAsActive()
    }
  } catch (error) {
    debug('Error handling application change: %O', error)
    refreshTable()
    markProjectAsActive()
  }
}

const dataSource = computed(() => {
  return {
    findAll: async (pageable: Pageable): Promise<IterablePage<EntityDefinition>> => {
      debug('dataSource.findAll called with projectId: %s, currentApplication: %s', projectId.value, APPLICATION_STATE.currentApplication?.id)
      const result = await Kinotic.entityDefinitions.findAllForProject(projectId.value, pageable)
      APPLICATION_STATE.entityDefinitionsCount = result.totalElements ?? 0
      return result
    },
    search: async (_searchText: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>> => {
      debug('dataSource.search called with projectId: %s, currentApplication: %s', projectId.value, APPLICATION_STATE.currentApplication?.id)
      const search = `projectId:${projectId.value} && ${searchText.value}`
      return Kinotic.entityDefinitions.search(search, pageable)
    }
  }
})

function refreshTable() {
  crudTable.value?.find?.()
}

function updateRouteQuery(newSearch: string) {
  searchText.value = newSearch
  const newQuery = { ...route.query }

  if (newSearch) {
    newQuery['search-entityDefinition'] = newSearch
  } else {
    delete newQuery['search-entityDefinition']
  }

  router.replace({ query: newQuery }).catch(() => {})
  refreshTable()
}

function openModal(item: EntityDefinition) {
  try {
    selectedEntityDefinition.value = item
    showModal.value = true
  } catch (e) {
    debug('Failed to open modal with entityDefinition: %O', e)
  }
}

function closeModal() {
  showModal.value = false
  selectedEntityDefinition.value = null
}

function openItemModal(item: EntityDefinition) {
  selectedEntityDefinition.value = item
  showItemModal.value = true
}

function closeItemModal() {
  showItemModal.value = false
  selectedEntityDefinition.value = null
}

function openPublishModal(item: EntityDefinition) {
  debug('openPublishModal called for item: %s', item.name);
  selectedEntityDefinition.value = item
  showPublishModal.value = true
}

function closePublishModal() {
  showPublishModal.value = false
  selectedEntityDefinition.value = null
}

function openUnpublishModal(item: EntityDefinition) {
  selectedEntityDefinition.value = item
  showUnpublishModal.value = true
}

function closeUnpublishModal() {
  showUnpublishModal.value = false
  selectedEntityDefinition.value = null
}

function onAddItem() {
  // this.$router.push("/new-entityDefinition")
}

function onEditItem(item: Identifiable<string>) {
  router.push(`${route.path}/edit/${item.id}`)
}

function handleRowClick(item: EntityDefinition) {
  if (item.published) {
    debug('Opening data modal for published entityDefinition');
    openModal(item)
  } else {
    debug('Opening publish modal for unpublished entityDefinition');
    openPublishModal(item)
  }
}

function toggleMenu(event: Event, item: EntityDefinition, index: number) {
  currentActionItem.value = item
  const menu = actionMenus.value[index]
  if (menu) {
    menu.toggle(event)
  }
}

async function publish(item: any) {
  item['publishing'] = true
  let table: any = crudTable.value
  try {
    await dataSource1.publish(item.id)
    table?.find()
    delete item['publishing']
  } catch (error: any) {
    delete item['publishing']
    table?.displayAlert(error.message)
  }
}

async function unPublish(item: any) {
  openUnpublishModal(item)
}

async function unpublishFromModal() {
  if (!selectedEntityDefinition.value) return;

  const item = selectedEntityDefinition.value as any;
  item["publishing"] = true;

  try {
    await dataSource1.unPublish(item.id);
    closeUnpublishModal();
    refreshTable();
    delete item["publishing"];
  } catch (error: any) {
    delete item["publishing"];
    debug('Error unpublishing entityDefinition: %O', error);
  }
}

function getActionMenu(item: EntityDefinition) {
  return [
    {
      label: item.published ? 'Unpublish' : 'Publish',
      icon: item.published ? 'pi pi-eye-slash' : 'pi pi-eye',
      command: () => (item.published ? unPublish(item) : publish(item))
    },
    {
      label: 'View',
      icon: 'pi pi-file',
      command: (e: any) => {
        e?.originalEvent?.stopPropagation?.()
        e?.originalEvent?.preventDefault?.()
        openItemModal(item)
      }
    }
  ]
}

async function publishFromModal() {
  if (!selectedEntityDefinition.value) return;

  const item = selectedEntityDefinition.value as any;
  item["publishing"] = true;

  try {
    await dataSource1.publish(item.id);
    closePublishModal();
    refreshTable();
    delete item["publishing"];
  } catch (error: any) {
    delete item["publishing"];
    debug('Error publishing entityDefinition: %O', error);
  }
}

const isPublishing = computed(() => {
  return (selectedEntityDefinition.value as any)?.publishing || false;
})

async function markProjectAsActive() {
  try {
    const header = instance?.proxy?.$root?.$refs?.header as { setActiveProjectById?: (appId: string, projId: string) => Promise<void> } | undefined
    if (header?.setActiveProjectById) {
      await header.setActiveProjectById(props.applicationId!, projectId.value)
    }
  } catch (e) {
    debug('Failed to mark active project: %O', e)
  }
}
</script>

<template>
  <div class="flex flex-1 flex-col">
    <CrudTable
      ref="crudTable"
      :data-source="dataSource"
      :headers="entityDefinitionTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddItem"
      @edit-item="onEditItem"
      @onRowClick="handleRowClick"
      :isShowAddNew="showNewEntityDefinitionButton"
      :createNewButtonText="newEntityDefinitionButtonText"
      emptyStateText="No entities yet for this project"
      rowHoverColor=""
      class="!text-sm"
    >
      <!-- createNewButtonText="New Entity" -->
      <template #item.name="{ item }">
        <span class="font-semibold">{{ item.name }}</span>
      </template>
      <template #item.created="{ item }">
        <span>
          {{ DatetimeUtil.formatMonthDayYear(item.created) }}
        </span>
      </template>
      <template #item.updated="{ item }">
        <span>
          {{ DatetimeUtil.formatRelativeDate(item.updated) }}
        </span>
      </template>
      <template #item.published="{ item }">
        <Tag
          :value="item.published ? 'Published' : 'Unpublished'"
          :severity="item.published ? 'success' : 'secondary'"
          class="px-2 py-1 text-sm"
          rounded
        />
      </template>
      <template #item.description="{ item }">
        <span>{{ item.description }}</span>
      </template>
      <template #additional-actions="{ item }">
        <div class="flex items-center justify-center">
          <Button
            icon="pi pi-ellipsis-v"
            @click.stop="(event) => toggleMenu(event, item, item.id)"
            aria-haspopup="true"
            :aria-controls="'action_menu_' + item.id"
            type="button"
            severity="secondary"
            variant="text"
          />
          <Menu
            :ref="(el) => (actionMenus[item.id] = el)"
            :model="getActionMenu(item)"
            :popup="true"
            :id="'action_menu_' + item.id"
          />
        </div>
      </template>
    </CrudTable>

    <EntityDefinitionItemModal
      v-if="showItemModal && selectedEntityDefinition"
      :item="selectedEntityDefinition"
      @close="closeItemModal"
    />

    <EntityDataViewModal
      v-if="showModal && selectedEntityDefinition"
      v-model="showModal"
      :title="selectedEntityDefinition?.name || 'Data View'"
      :entity-props="{ entityDefinitionId: selectedEntityDefinition?.id }"
      @close="closeModal"
    />

    <Dialog
      v-model:visible="showPublishModal"
      modal
      :style="{ width: '400px' }"
      :closable="false"
    >
      <template #header>
        <div class="flex items-center">
          <span>{{ selectedEntityDefinition?.name || 'Entity' }}</span>
          <div 
            class="ml-2 px-3 py-1 rounded-full text-sm font-medium"
            :class="selectedEntityDefinition?.published ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'"
          >
            {{ selectedEntityDefinition?.published ? 'Published' : 'Unpublished' }}
          </div>
        </div>
      </template>
      <div class="mb-6">
        <p class="text-gray-700">
          The EntityDefinition must be published before it can contain data. Would you like to publish it?
        </p>
      </div>
      
      <template #footer>
        <div class="flex justify-end gap-3">
          <Button
            label="Cancel"
            severity="secondary"
            @click="closePublishModal"
            class="px-4 py-2"
          />
          <Button
            label="Publish"
            @click="publishFromModal"
            :loading="isPublishing"
            class="px-4 py-2"
          />
        </div>
      </template>
    </Dialog>

    <Dialog
      v-model:visible="showUnpublishModal"
      modal
      :style="{ width: '450px' }"
      :closable="false"
    >
      <template #header>
        <div class="flex items-center">
          <span>{{ selectedEntityDefinition?.name || 'Entity' }}</span>
          <div 
            class="ml-2 px-3 py-1 rounded-full text-sm font-medium"
            :class="selectedEntityDefinition?.published ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'"
          >
            {{ selectedEntityDefinition?.published ? 'Published' : 'Unpublished' }}
          </div>
        </div>
      </template>
      <div class="mb-6">
        <div class="flex items-start gap-3">
          <div class="flex-shrink-0">
            <svg class="w-6 h-6 text-red-600 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path>
            </svg>
          </div>
          <div>
            <p class="text-gray-700 font-medium mb-2">Are you sure you want to unpublish this entityDefinition?</p>
            <p class="text-gray-600 text-sm">
              All data saved under this EntityDefinition will be permanently deleted. This action cannot be undone.
            </p>
          </div>
        </div>
      </div>
      
      <template #footer>
        <div class="flex justify-end gap-3">
          <Button
            label="Cancel"
            severity="secondary"
            @click="closeUnpublishModal"
            class="px-4 py-2"
          />
          <Button
            label="Unpublish"
            severity="danger"
            @click="unpublishFromModal"
            :loading="isPublishing"
            class="px-4 py-2"
          />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.p-row-even,
.p-row-odd {
  cursor: pointer;
}
</style>
