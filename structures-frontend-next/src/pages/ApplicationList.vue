<script lang="ts">
import { Component, Vue, Ref, Watch } from "vue-facing-decorator";
import CrudTable from "@/components/CrudTable.vue";
import ContainerMedium from "@/components/ContainerMedium.vue";
import ApplicationSidebar from "@/components/ApplicationSidebar.vue";
import GraphQLModal from "@/components/modals/GraphQLModal.vue";
import OpenAPIModal from "@/components/modals/OpenAPIModal.vue";
import {
  Structures,
  type IApplicationService,
  type Application,
} from "@kinotic/structures-api";
import { APPLICATION_STATE } from "@/states/IApplicationState";
import { mdiGraphql, mdiApi } from "@mdi/js";
import { onClickOutside } from "@vueuse/core";
import type { CrudHeader } from "@/types/CrudHeader";
import type { Identifiable } from "@kinotic/continuum-client";
import { shallowRef } from "vue";
import DatetimeUtil from "@/util/DatetimeUtil";
import { createDebug } from "@/util/debug";

const debug = createDebug('application-list');

@Component({
  components: {
    CrudTable,
    ContainerMedium,
    ApplicationSidebar,
    GraphQLModal,
    OpenAPIModal,
  },
})
export default class NamespaceList extends Vue {
  headers: CrudHeader[] = [
    { field: "id", header: "Name", sortable: false },
    { field: "description", header: "Description", sortable: false },
    { field: "created", header: "Created", sortable: false },
    { field: "updated", header: "Updated", sortable: false },
  ];

  dataSource: IApplicationService = Structures.getApplicationService();
  icons = { graph: mdiGraphql, api: mdiApi };
  showGraphQLModal = false;
  showOpenAPIModal = false;
  showSidebar = false;
  searchText: string = (this?.$route?.query.search as string) || "";
  itemCount: number = 0;
  @Ref("sidebarWrapper") sidebarWrapper!: HTMLElement;
  @Ref("crudTable") crudTable!: InstanceType<typeof CrudTable>;
  public DatetimeUtil = DatetimeUtil;

  async mounted(): Promise<void> {
    try {
      this.refreshTable();
      const el = shallowRef<HTMLElement | null>(this.sidebarWrapper);

      onClickOutside(el, () => {
        if (this.showSidebar) {
          this.onSidebarClose();
        }
      });

      if (this?.$route?.query.created === "true") {
        this.$router.replace({ query: {} });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      debug('Initialization error: %s', message);
    }
  }

  @Watch("$route.query.search")
  onRouteSearchQueryChanged(newVal: string) {
    this.searchText = newVal || "";
  }

  private refreshTable(): void {
    this.crudTable?.find();
  }
  onItemsCount(count: number): void {
    this.itemCount = count;
  }

  updateRouteQuery(search: string) {
    const query = { ...this?.$route?.query };

    if (search) {
      query.search = search;
    } else {
      delete query.search;
    }

    this.$router.replace({ query });
  }
  get shouldShowPagination(): boolean {
    return this.itemCount > 3;
  }

  onAddItem(): void {
    this.showSidebar = true;
  }

  async toApplicationPage(item: Identifiable<string>): Promise<void> {
    try {
      const appId = item.id ?? "";
      const app = await this.dataSource.findById(appId);
      APPLICATION_STATE.currentApplication = app;
      this.$router.push(`/application/${encodeURIComponent(appId)}`);
    } catch (e) {
      debug('Failed to navigate to application: %O', e);
    }
  }

  onSidebarClose(): void {
    this.showSidebar = false;
  }

  onApplicationSubmit(created: Application): void {
    if (created && created.id) {
      const exists = APPLICATION_STATE.allApplications.some(
        (a) => a.id === created.id
      );
      if (!exists) {
        APPLICATION_STATE.allApplications = [
          created,
          ...APPLICATION_STATE.allApplications,
        ];
      }
    }
    this.refreshTable();
    this.showSidebar = false;
  }

  onEditItem(item: Identifiable<string>): void {
    this.$router.push(`${this.$route.path}/edit/${item.id}`);
  }

  openGraphQL(): void {
    this.showGraphQLModal = true;
  }

  closeGraphQL(): void {
    this.showGraphQLModal = false;
  }

  openOpenAPI(): void {
    this.showOpenAPIModal = true;
  }

  closeOpenAPI(): void {
    this.showOpenAPIModal = false;
  }
}
</script>

<template>
  <ContainerMedium>
    <h1 class="text-2xl font-semibold mb-5 text-surface-950">Applications</h1>
    <CrudTable
      ref="crudTable"
      createNewButtonText="New application"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="headers"
      :singleExpand="false"
      :enableViewSwitcher="true"
      emptyStateText="No applications yet"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddItem"
      @edit-item="onEditItem"
      @onRowClick="toApplicationPage"
      class="!text-sm"
      :show-pagination="false"
    >
      <template #item.id="{ item }">
        <span>{{ item.id }}</span>
      </template>
      <template #item.description="{ item }">
        <span
          class="block max-w-[300px] sm:max-w-[500px] md:max-w-[190px] lg:max-w-[390px] xl:max-w-[590px] truncate"
        >
          {{ item.description }}
        </span>
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
      <template #additional-actions="{ item }">
        <Button
          v-if="item.enableGraphQL"
          text
          title="GraphQL"
          @click="openGraphQL"
        >
          <img class="!w-[24px] !h-[24px]" src="@/assets/graphql.svg" />
        </Button>

        <Button
          v-if="item.enableOpenAPI"
          text
          title="OpenAPI"
          @click="openOpenAPI"
        >
          <img class="!w-[24px] !h-[24px]" src="@/assets/scalar.svg" />
        </Button>
      </template>
    </CrudTable>

    <GraphQLModal :visible="showGraphQLModal" @close="closeGraphQL" />
    <OpenAPIModal :visible="showOpenAPIModal" @close="closeOpenAPI" />

    <div v-show="showSidebar" ref="sidebarWrapper">
      <ApplicationSidebar
        :visible="showSidebar"
        @close="onSidebarClose"
        @submit="onApplicationSubmit"
      />
    </div>
  </ContainerMedium>
</template>

<style scoped>
.p-row-even,
.p-row-odd {
  cursor: pointer;
}

:deep(.p-datatable .p-datatable-tbody > tr) {
  height: 64px;
}

:deep(.p-datatable .p-datatable-tbody > tr > td) {
  vertical-align: middle;
}

:deep(.p-datatable .p-datatable-tbody > tr > td:last-child) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64px;
}
</style>
