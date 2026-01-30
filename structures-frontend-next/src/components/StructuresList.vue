<script lang="ts">
import { Component, Vue, Prop, Ref, Watch } from "vue-facing-decorator";
import CrudTable from "@/components/CrudTable.vue";
import StructureDataViewModal from "@/components/modals/StructureDataViewModal.vue";
import StructureItemModal from "@/components/modals/StructureItemModal.vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import type {
  Identifiable,
  IterablePage,
  Pageable,
} from "@kinotic/continuum-client";
import { APPLICATION_STATE } from "@/states/IApplicationState";
import { Structure, Structures, type IStructureService } from "@kinotic/structures-api";
import type { CrudHeader } from "@/types/CrudHeader";
import DatetimeUtil from "@/util/DatetimeUtil";

@Component({
  components: { CrudTable, StructureDataViewModal, StructureItemModal, Dialog, Button },
})
export default class StructuresList extends Vue {
  @Prop({ required: true }) applicationId!: string;
  @Prop({ required: false, default: undefined }) projectId?: string;
  @Prop({ required: false, default: "" }) initialSearch!: string;
  @Prop({ required: false, default: false }) showNewStructureButton!: boolean;
  @Prop({ required: false, default: "New Structure" }) newStructureButtonText!: string;

  @Ref("crudTable") crudTable!: InstanceType<typeof CrudTable>;

  structureTableHeaders: CrudHeader[] = [
    { field: "name", header: "Structure name", sortable: true },
    { field: "projectId", header: "Project", sortable: true },
    { field: "description", header: "Description", sortable: false },
    { field: "created", header: "Created", sortable: false },
    { field: "updated", header: "Updated", sortable: false },
  { field: "published", header: "Status", sortable: false, centered: true },

  ];

  showModal = false;
  showItemModal = false;
  showPublishModal = false;
  showUnpublishModal = false;
  selectedStructure: Structure | null = null;
  searchText = "";
  isInitialized = false;

  actionMenus: any[] = [];
  currentActionItem: Structure | null = null;

  mounted(): void {
    this.searchText = this.initialSearch || "";
    this.isInitialized = true;
  }

  @Watch("initialSearch")
  onInitialSearchChanged(newVal: string) {
    if (this.isInitialized) {
      this.searchText = newVal || "";
      this.refreshTable();
    }
  }

  @Watch("applicationId")
  onApplicationIdChanged() {
    this.refreshTable();
  }

  @Watch("projectId")
  onProjectIdChanged() {
    this.refreshTable();
  }

  private dataSource1: IStructureService = Structures.getStructureService();

  get dataSource() {
    return {
      findAll: async (pageable: Pageable): Promise<IterablePage<Structure>> => {
        const service = Structures.getStructureService();
        const result = this.projectId
          ? await service.findAllForProject(this.projectId, pageable)
          : await service.findAllForApplication(this.applicationId, pageable);

        APPLICATION_STATE.structuresCount = result.totalElements ?? 0;
        return result;
      },
      search: async (
        _searchText: string,
        pageable: Pageable
      ): Promise<IterablePage<Structure>> => {
        const filter = this.projectId
          ? `projectId:${this.projectId}`
          : `applicationId:${this.applicationId}`;
        const query = `${filter} && ${this.searchText}`;
        return Structures.getStructureService().search(query, pageable);
      },
    };
  }

  get structuresCount() {
    return APPLICATION_STATE.structuresCount;
  }

  public DatetimeUtil = DatetimeUtil;

  refreshTable(): void {
    if (this.crudTable?.find) {
      this.crudTable.find();
    }
  }

  updateRouteQuery(newSearch: string): void {
    this.searchText = newSearch;
    const query = { ...this.$route.query };

    if (newSearch) {
      query["search-structure"] = newSearch;
    } else {
      delete query["search-structure"];
    }

    this.$router.replace({ query }).catch(() => {});
    this.refreshTable();
  }

  openModal(item: Structure) {
    this.selectedStructure = item;
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
    this.selectedStructure = null;
  }

  openItemModal(item: Structure) {
    this.selectedStructure = item;
    this.showItemModal = true;
  }

  closeItemModal() {
    this.showItemModal = false;
    this.selectedStructure = null;
  }

  openPublishModal(item: Structure) {
    this.selectedStructure = item;
    this.showPublishModal = true;
  }

  closePublishModal() {
    this.showPublishModal = false;
    this.selectedStructure = null;
  }

  openUnpublishModal(item: Structure) {
    this.selectedStructure = item;
    this.showUnpublishModal = true;
  }

  closeUnpublishModal() {
    this.showUnpublishModal = false;
    this.selectedStructure = null;
  }

  handleRowClick(item: Structure): void {
    if (item.published) {
      this.openModal(item);
    } else {
      this.openPublishModal(item);
    }
  }

  onEditItem(item: Identifiable<string>): void {
    this.$router.push(`${this.$route.path}/edit/${item.id}`);
  }

  toggleMenu(event: Event, item: Structure, index: number) {
    this.currentActionItem = item;
    const menu = this.actionMenus[index];
    if (menu) {
      menu.toggle(event);
    }
  }

  public async publish(item: any) {
    item["publishing"] = true;
    let table: any = this.$refs?.crudTable;
    try {
      await this.dataSource1.publish(item.id);
      table?.find();
      delete item["publishing"];
    } catch (error: any) {
      delete item["publishing"];
      table?.displayAlert(error.message);
    }
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
      console.error('Error publishing structure:', error);
      // You could add a toast notification here if needed
    }
  }

  public async unPublish(item: any) {
    this.openUnpublishModal(item);
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
      console.error('Error unpublishing structure:', error);
    }
  }

  getActionMenu(item: Structure) {
    return [
      {
        label: item.published ? "Unpublish" : "Publish",
        icon: item.published ? "pi pi-eye-slash" : "pi pi-eye",
        command: () =>
          item.published ? this.unPublish(item) : this.publish(item),
      },
      {
        label: "View",
        icon: "pi pi-file",
        command: (e: any) => {
          e?.originalEvent?.stopPropagation?.();
          e?.originalEvent?.preventDefault?.();
          this.openItemModal(item);
        }
      },
    ];
  }

  get isPublishing() {
    return (this.selectedStructure as any)?.publishing || false;
  }
}
</script>

<template>
  <div>
    <CrudTable
      ref="crudTable"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="structureTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @edit-item="onEditItem"
      @onRowClick="handleRowClick"
      :isShowAddNew="showNewStructureButton"
      :createNewButtonText="newStructureButtonText"
      class="!text-sm"
      emptyStateText="No structures yet"
    >
      <template #item.created="{ item }">
        <span>{{ DatetimeUtil.formatMonthDayYear(item.created) }}</span>
      </template>

      <template #item.updated="{ item }">
        <span>{{ DatetimeUtil.formatRelativeDate(item.updated) }}</span>
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

    <StructureDataViewModal
      v-if="selectedStructure"
      v-model="showModal"
      :title="selectedStructure?.name || 'Data View'"
      :entity-props="{ structureId: selectedStructure?.id }"
      @close="closeModal"
    />

    <StructureItemModal
      v-if="showItemModal && selectedStructure"
      :item="selectedStructure"
      @close="closeItemModal"
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

    <!-- Unpublish Modal -->
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