<script lang="ts">
import { Component, Vue, Prop, Watch, Ref } from 'vue-facing-decorator'
import CrudTable from '@/components/CrudTable.vue'
import StructureItemModal from '@/components/modals/StructureItemModal.vue'
import StructureDataViewModal from '@/components/modals/StructureDataViewModal.vue'
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import { Structure, Structures } from '@mindignited/structures-api'
import type { CrudHeader } from '@/types/CrudHeader'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import type { Identifiable, IterablePage, Pageable } from '@mindignited/continuum-client'
import DatetimeUtil from "@/util/DatetimeUtil"
import { createDebug } from '@/util/debug'

const debug = createDebug('project-structures-table');

@Component({
  components: { CrudTable, StructureItemModal, StructureDataViewModal, Dialog, Button }
})
export default class ProjectStructuresTable extends Vue {
  @Prop({ required: true }) applicationId!: string
  @Prop({ required: false, default: false }) showNewStructureButton!: boolean
  @Prop({ required: false, default: "New Structure" }) newStructureButtonText!: string
  @Ref('crudTable') crudTable!: InstanceType<typeof CrudTable>

  get projectId(): string {
    return this.$route.params.projectId as string
  }

  structureTableHeaders: CrudHeader[] = [
    { field: 'name', header: 'Structure Name', sortable: true },
    { field: 'description', header: 'Description', sortable: false },
    { field: 'created', header: 'Created', sortable: false },
    { field: 'updated', header: 'Updated', sortable: false },
    { field: 'published', header: 'Status', sortable: false, centered: true }
  ]

  searchText: string = ''
  showModal = false
  showItemModal = false
  showPublishModal = false
  showUnpublishModal = false
  selectedStructure: Structure | null = null
  isInitialized = false
  public DatetimeUtil = DatetimeUtil
  actionMenus: any[] = []
  currentActionItem: Structure | null = null
  private dataSource1 = Structures.getStructureService()

  mounted() {
    this.searchText = (this.$route.query['search-structure'] as string) || ''
    this.isInitialized = true
    this.refreshTable()
    this.markProjectAsActive()
  }

  @Watch('$route.query.search-structure')
  onSearchParamChanged(val: string) {
    if (this.isInitialized) {
      this.searchText = val || ''
      this.refreshTable()
    }
  }

  @Watch('$route.params.projectId', { immediate: true })
  onProjectIdChange() {
    this.refreshTable()
    this.markProjectAsActive()
  }

  @Watch('applicationId', { immediate: true })
  onApplicationIdChange(newApplicationId: string, oldApplicationId: string) {
    debug('applicationId changed from %s to %s', oldApplicationId, newApplicationId)
    this.refreshTable()
    this.markProjectAsActive()
  }

  @Watch('APPLICATION_STATE.currentApplication', { immediate: true })
  async onGlobalApplicationChange(newApp: any, oldApp: any) {
    debug('APPLICATION_STATE.currentApplication changed from %s to %s', oldApp?.id, newApp?.id)
    
    if (this.isProjectStructuresPage && newApp && newApp.id !== oldApp?.id) {
      await this.handleApplicationChangeForProjectStructures(newApp)
    } else {
      this.refreshTable()
      this.markProjectAsActive()
    }
  }

  get isProjectStructuresPage(): boolean {
    return /^\/application\/[^/]+\/project\/[^/]+\/structures$/.test(this.$route.path)
  }

  async handleApplicationChangeForProjectStructures(newApp: any) {
    try {
      debug('Handling application change for ProjectStructures page')
      
      const pageable = { pageNumber: 0, pageSize: 1 } as any
      const result = await Structures.getProjectService().findAllForApplication(newApp.id, pageable)
      const firstProject = result.content?.[0]
      
      if (firstProject) {
        debug('Found first project: %s, navigating to it', firstProject.name)
        
        const applicationId = newApp.id
        const projectId = firstProject.id ?? ''
        await this.$router.push(`/application/${encodeURIComponent(applicationId)}/project/${encodeURIComponent(projectId)}/structures`)
      } else {
        debug('No projects found for application: %s', newApp.id)
        this.refreshTable()
        this.markProjectAsActive()
      }
    } catch (error) {
      debug('Error handling application change: %O', error)
      this.refreshTable()
      this.markProjectAsActive()
    }
  }

  get dataSource() {
    return {
      findAll: async (pageable: Pageable): Promise<IterablePage<Structure>> => {
        debug('dataSource.findAll called with projectId: %s, currentApplication: %s', this.projectId, APPLICATION_STATE.currentApplication?.id)
        const result = await Structures.getStructureService().findAllForProject(this.projectId, pageable)
        APPLICATION_STATE.structuresCount = result.totalElements ?? 0
        return result
      },
      search: async (_searchText: string, pageable: Pageable): Promise<IterablePage<Structure>> => {
        debug('dataSource.search called with projectId: %s, currentApplication: %s', this.projectId, APPLICATION_STATE.currentApplication?.id)
        const search = `projectId:${this.projectId} && ${this.searchText}`
        return Structures.getStructureService().search(search, pageable)
      }
    }
  }

  refreshTable() {
    this.crudTable?.find?.()
  }

  updateRouteQuery(newSearch: string) {
    this.searchText = newSearch
    const newQuery = { ...this.$route.query }

    if (newSearch) {
      newQuery['search-structure'] = newSearch
    } else {
      delete newQuery['search-structure']
    }

    this.$router.replace({ query: newQuery }).catch(() => {})
    this.refreshTable()
  }

  openModal(item: Structure) {
    try {
      this.selectedStructure = item
      this.showModal = true
    } catch (e) {
      debug('Failed to open modal with structure: %O', e)
    }
  }

  closeModal() {
    this.showModal = false
    this.selectedStructure = null
  }

  openItemModal(item: Structure) {
    this.selectedStructure = item
    this.showItemModal = true
  }

  closeItemModal() {
    this.showItemModal = false
    this.selectedStructure = null
  }

  openPublishModal(item: Structure) {
    debug('openPublishModal called for item: %s', item.name);
    this.selectedStructure = item
    this.showPublishModal = true
  }

  closePublishModal() {
    this.showPublishModal = false
    this.selectedStructure = null
  }

  openUnpublishModal(item: Structure) {
    this.selectedStructure = item
    this.showUnpublishModal = true
  }

  closeUnpublishModal() {
    this.showUnpublishModal = false
    this.selectedStructure = null
  }

  onAddItem() {
    // this.$router.push("/new-structure")
  }

  onEditItem(item: Identifiable<string>) {
    this.$router.push(`${this.$route.path}/edit/${item.id}`)
  }

  handleRowClick(item: Structure) {
    if (item.published) {
      debug('Opening data modal for published structure');
      this.openModal(item)
    } else {
      debug('Opening publish modal for unpublished structure');
      this.openPublishModal(item)
    }
  }

  toggleMenu(event: Event, item: Structure, index: number) {
    this.currentActionItem = item
    const menu = this.actionMenus[index]
    if (menu) {
      menu.toggle(event)
    }
  }

  async publish(item: any) {
    item['publishing'] = true
    let table: any = this.$refs?.crudTable
    try {
      await this.dataSource1.publish(item.id)
      table?.find()
      delete item['publishing']
    } catch (error: any) {
      delete item['publishing']
      table?.displayAlert(error.message)
    }
  }

  async unPublish(item: any) {
    this.openUnpublishModal(item)
  }

  async unpublishFromModal() {
    if (!this.selectedStructure) return;
    
    const item = this.selectedStructure as any;
    item["publishing"] = true;
    
    try {
      await this.dataSource1.unPublish(item.id);
      this.closeUnpublishModal();
      this.refreshTable();
      delete item["publishing"];
    } catch (error: any) {
      delete item["publishing"];
      debug('Error unpublishing structure: %O', error);
    }
  }

  getActionMenu(item: Structure) {
    return [
      {
        label: item.published ? 'Unpublish' : 'Publish',
        icon: item.published ? 'pi pi-eye-slash' : 'pi pi-eye',
        command: () => (item.published ? this.unPublish(item) : this.publish(item))
      },
      {
        label: 'View',
        icon: 'pi pi-file',
        command: (e: any) => {
          e?.originalEvent?.stopPropagation?.()
          e?.originalEvent?.preventDefault?.()
          this.openItemModal(item)
        }
      }
    ]
  }

  async publishFromModal() {
    if (!this.selectedStructure) return;
    
    const item = this.selectedStructure as any;
    item["publishing"] = true;
    
    try {
      await this.dataSource1.publish(item.id);
      this.closePublishModal();
      this.refreshTable();
      delete item["publishing"];
    } catch (error: any) {
      delete item["publishing"];
      debug('Error publishing structure: %O', error);
    }
  }

  get isPublishing() {
    return (this.selectedStructure as any)?.publishing || false;
  }

  async markProjectAsActive() {
    try {
      const header = this.$root?.$refs?.header as { setActiveProjectById?: (appId: string, projId: string) => Promise<void> } | undefined
      if (header?.setActiveProjectById) {
        await header.setActiveProjectById(this.applicationId, this.projectId)
      }
    } catch (e) {
      debug('Failed to mark active project: %O', e)
    }
  }
}
</script>

<template>
  <div>
    <CrudTable
      ref="crudTable"
      :data-source="dataSource"
      :headers="structureTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddItem"
      @edit-item="onEditItem"
      @onRowClick="handleRowClick"
      :isShowAddNew="showNewStructureButton"
      :createNewButtonText="newStructureButtonText"
      emptyStateText="No structures yet for this project"
      rowHoverColor=""
      class="!text-sm"
    >
      <!-- createNewButtonText="New Structure" -->
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
        <div class="w-full text-center">
          <Tag
            :value="item.published ? 'Published' : 'Unpublished'"
            :severity="item.published ? 'success' : 'secondary'"
            class="px-2 py-1 text-sm"
            rounded
          />
        </div>
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

    <StructureItemModal
      v-if="showItemModal && selectedStructure"
      :item="selectedStructure"
      @close="closeItemModal"
    />

    <StructureDataViewModal
      v-if="showModal && selectedStructure"
      v-model="showModal"
      :title="selectedStructure?.name || 'Data View'"
      :entity-props="{ structureId: selectedStructure?.id }"
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
          <span>{{ selectedStructure?.name || 'Structure' }}</span>
          <div 
            class="ml-2 px-3 py-1 rounded-full text-sm font-medium"
            :class="selectedStructure?.published ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'"
          >
            {{ selectedStructure?.published ? 'Published' : 'Unpublished' }}
          </div>
        </div>
      </template>
      <div class="mb-6">
        <p class="text-gray-700">
          The structure must be published before it can contain data. Would you like to publish it?
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
          <span>{{ selectedStructure?.name || 'Structure' }}</span>
          <div 
            class="ml-2 px-3 py-1 rounded-full text-sm font-medium"
            :class="selectedStructure?.published ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'"
          >
            {{ selectedStructure?.published ? 'Published' : 'Unpublished' }}
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
            <p class="text-gray-700 font-medium mb-2">Are you sure you want to unpublish this structure?</p>
            <p class="text-gray-600 text-sm">
              All data saved under this structure will be permanently deleted. This action cannot be undone.
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
