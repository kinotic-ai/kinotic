<script lang="ts">
import { defineComponent } from "vue";
import { CrudTable } from "@kinotic-ai/frontend-common";
import EntityDataViewModal from "@/components/modals/EntityDataViewModal.vue";
import EntityDefinitionItemModal from "@/components/modals/EntityDefinitionItemModal.vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import Tag from "primevue/tag";
import type {
  IterablePage,
  Pageable,
} from "@kinotic-ai/core";
import { APPLICATION_STATE } from "@/states/IApplicationState";
import { Kinotic } from "@kinotic-ai/core";
import { EntityDefinition, type IEntityDefinitionService } from "@kinotic-ai/os-api";
import type { CrudHeader } from "@kinotic-ai/frontend-common";
import { DatetimeUtil } from "@kinotic-ai/frontend-common";
import { createDebug } from "@kinotic-ai/frontend-common";

const debug = createDebug("entityDefinitions-list");

export default defineComponent({
  name: "EntityDefinitionsList",
  components: {
    Button,
    CrudTable,
    Dialog,
    EntityDataViewModal,
    EntityDefinitionItemModal,
    Tag,
  },
  props: {
    applicationId: {
      type: String,
      required: true,
    },
    projectId: {
      type: String,
      required: false,
      default: undefined,
    },
    initialSearch: {
      type: String,
      required: false,
      default: "",
    },
    showNewEntityDefinitionButton: {
      type: Boolean,
      required: false,
      default: false,
    },
    newEntityDefinitionButtonText: {
      type: String,
      required: false,
      default: "New Entity",
    },
  },
  data() {
    return {
      DatetimeUtil,
      dataSource1: Kinotic.entityDefinitions as IEntityDefinitionService,
      isInitialized: false,
      searchText: "",
      selectedEntityDefinition: null as EntityDefinition | null,
      showItemModal: false,
      showModal: false,
      showPublishModal: false,
      showUnpublishModal: false,
      entityDefinitionTableHeaders: [
        { field: "name", header: "Entity name", sortable: true },
        { field: "projectId", header: "Project", sortable: true },
        { field: "description", header: "Description", sortable: false },
        { field: "created", header: "Created", sortable: false },
        { field: "updated", header: "Updated", sortable: false },
        { field: "published", header: "Status", sortable: false, centered: true },
      ] as CrudHeader[],
    };
  },
  computed: {
    dataSource() {
      return {
        findAll: async (pageable: Pageable): Promise<IterablePage<EntityDefinition>> => {
          const service = Kinotic.entityDefinitions;
          const result = this.projectId
            ? await service.findAllForProject(this.projectId, pageable)
            : await service.findAllForApplication(this.applicationId, pageable);

          APPLICATION_STATE.entityDefinitionsCount = result.totalElements ?? 0;
          return result;
        },
        search: async (
          _searchText: string,
          pageable: Pageable
        ): Promise<IterablePage<EntityDefinition>> => {
          const filter = this.projectId
            ? `projectId:${this.projectId}`
            : `applicationId:${this.applicationId}`;
          const query = `${filter} && ${this.searchText}`;
          return Kinotic.entityDefinitions.search(query, pageable);
        },
      };
    },
    isPublishing(): boolean {
      return (this.selectedEntityDefinition as any)?.publishing || false;
    },
    entityDefinitionsCount(): number {
      return APPLICATION_STATE.entityDefinitionsCount;
    },
  },
  watch: {
    applicationId() {
      this.refreshTable();
    },
    initialSearch(newVal: string) {
      if (this.isInitialized) {
        this.searchText = newVal || "";
        this.refreshTable();
      }
    },
    projectId() {
      this.refreshTable();
    },
  },
  mounted() {
    this.searchText = this.initialSearch || "";
    this.isInitialized = true;
  },
  methods: {
    refreshTable(): void {
      const crudTable = this.$refs.crudTable as InstanceType<typeof CrudTable> | undefined;
      crudTable?.find?.();
    },
    updateRouteQuery(newSearch: string): void {
      this.searchText = newSearch;
      const query = { ...this.$route.query };

      if (newSearch) {
        query["search-entityDefinition"] = newSearch;
      } else {
        delete query["search-entityDefinition"];
      }

      this.$router.replace({ query }).catch(() => {});
      this.refreshTable();
    },
    openModal(item: EntityDefinition): void {
      this.selectedEntityDefinition = item;
      this.showModal = true;
    },
    closeModal(): void {
      this.showModal = false;
      this.selectedEntityDefinition = null;
    },
    openItemModal(item: EntityDefinition): void {
      this.selectedEntityDefinition = item;
      this.showItemModal = true;
    },
    closeItemModal(): void {
      this.showItemModal = false;
      this.selectedEntityDefinition = null;
    },
    openPublishModal(item: EntityDefinition): void {
      this.selectedEntityDefinition = item;
      this.showPublishModal = true;
    },
    closePublishModal(): void {
      this.showPublishModal = false;
      this.selectedEntityDefinition = null;
    },
    openUnpublishModal(item: EntityDefinition): void {
      this.selectedEntityDefinition = item;
      this.showUnpublishModal = true;
    },
    closeUnpublishModal(): void {
      this.showUnpublishModal = false;
      this.selectedEntityDefinition = null;
    },
    handleRowClick(item: EntityDefinition): void {
      if (item.published) {
        this.openModal(item);
      } else {
        this.openPublishModal(item);
      }
    },
    async publish(item: any): Promise<void> {
      item["publishing"] = true;
      const table = this.$refs?.crudTable as any;
      try {
        await this.dataSource1.publish(item.id);
        table?.find?.();
        delete item["publishing"];
      } catch (error: any) {
        delete item["publishing"];
        table?.displayAlert?.(error.message);
      }
    },
    async publishFromModal(): Promise<void> {
      if (!this.selectedEntityDefinition) return;

      const item = this.selectedEntityDefinition as any;
      item["publishing"] = true;

      try {
        await this.dataSource1.publish(item.id);
        this.closePublishModal();
        this.refreshTable();
        delete item["publishing"];
      } catch (error: any) {
        delete item["publishing"];
        debug("Error publishing entityDefinition: %O", error);
      }
    },
    async unPublish(item: any): Promise<void> {
      this.openUnpublishModal(item);
    },
    async unpublishFromModal(): Promise<void> {
      if (!this.selectedEntityDefinition) return;

      const item = this.selectedEntityDefinition as any;
      item["publishing"] = true;

      try {
        await this.dataSource1.unPublish(item.id);
        this.closeUnpublishModal();
        this.refreshTable();
        delete item["publishing"];
      } catch (error: any) {
        delete item["publishing"];
        debug("Error unpublishing entityDefinition: %O", error);
      }
    },
    getActionMenu(item: EntityDefinition) {
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
          },
        },
      ];
    },
  },
});
</script>

<template>
  <div class="flex flex-1 flex-col">
    <CrudTable
      ref="crudTable"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="entityDefinitionTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @onRowClick="handleRowClick"
      :isShowAddNew="showNewEntityDefinitionButton"
      :createNewButtonText="newEntityDefinitionButtonText"
      :row-actions="getActionMenu"
      class="!text-sm"
      emptyStateText="No entities yet"
    >
      <template #item.created="{ item }">
        <span>{{ DatetimeUtil.formatMonthDayYear(item.created) }}</span>
      </template>

      <template #item.updated="{ item }">
        <span>{{ DatetimeUtil.formatRelativeDate(item.updated) }}</span>
      </template>

      <template #item.published="{ item }">
        <Tag
          :value="item.published ? 'Published' : 'Unpublished'"
          :severity="item.published ? 'success' : 'secondary'"
          class="px-2 py-1 text-sm"
          rounded
        />
      </template>
    </CrudTable>

    <EntityDataViewModal
      v-if="selectedEntityDefinition"
      v-model="showModal"
      :title="selectedEntityDefinition?.name || 'Data View'"
      :entity-props="{ entityDefinitionId: selectedEntityDefinition?.id }"
      @close="closeModal"
    />

    <EntityDefinitionItemModal
      v-if="showItemModal && selectedEntityDefinition"
      :item="selectedEntityDefinition"
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

    <!-- Unpublish Modal -->
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