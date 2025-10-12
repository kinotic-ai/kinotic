<script lang="ts">
import {
  Vue,
  Prop,
  Emit,
  toNative,
  Component,
  Watch,
} from "vue-facing-decorator";

import DataTable, { type DataTablePageEvent } from "primevue/datatable";
import Column from "primevue/column";
import Button from "primevue/button";
import Toolbar from "primevue/toolbar";
import InputText from "primevue/inputtext";
import ConfirmDialog from "primevue/confirmdialog";
import Card from "primevue/card";
import Paginator, { type PageState } from "primevue/paginator";
import SelectButton from "primevue/selectbutton";
import { useToast } from "primevue/usetoast";

import {
  type IDataSource,
  type Identifiable,
  Order,
  type Page,
  Pageable,
  Direction,
  DataSourceUtils,
} from "@kinotic/continuum-client";

import type { CrudHeader } from "@/types/CrudHeader";
import type { DescriptiveIdentifiable } from "@/types/DescriptiveIdentifiable";

@Component({
  components: {
    DataTable,
    Column,
    Button,
    Toolbar,
    InputText,
    ConfirmDialog,
    Card,
    Paginator,
    SelectButton,
  },
})
class CrudTable extends Vue {
  @Prop({ required: true }) dataSource!: IDataSource<DescriptiveIdentifiable>
  @Prop({ required: true }) headers!: CrudHeader[]
  @Prop({ default: false }) multiSort!: boolean
  @Prop({ default: true }) mustSort!: boolean
  @Prop({ default: false }) singleExpand!: boolean
  @Prop({ default: false }) disableModifications!: boolean
  @Prop({ default: true }) isShowAddNew!: boolean
  @Prop({ default: true }) isShowDelete!: boolean
  @Prop({ default: '' }) initialSearch!: string
  @Prop({ default: '#f5f5f5' }) rowHoverColor!: string
  @Prop({ default: 'Add new' }) createNewButtonText!: string
  @Prop({ default: false }) enableViewSwitcher!: boolean
  @Prop({ default: 'No items yet' }) emptyStateText!: string
  @Prop({ default: '' }) search!: string
  @Prop({ default: true }) showPagination!: boolean
  @Prop({ default: true }) enableRowHover!: boolean
  @Prop({ default: 10 }) defaultPageSize!: number

  private toast = useToast()

  getRowClass() {
    return {
      "dynamic-hover": this.enableRowHover,
      "transition-all": true,
    };
  }

  items: DescriptiveIdentifiable[] = [];
  totalItems = 0;
  loading = false;
  initialSearchCompleted = false;
  searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  activeView: "burger" | "column" = "burger";
  searchText: string | null = "";
  options = {
    page: 0,
    rows: 10,
    first: 0,
    sortField: "",
    sortOrder: 1 as 1 | -1,
  };

  viewOptions = [
    { icon: "pi pi-bars", value: "burger" },
    { icon: "pi pi-th-large", value: "column" },
  ];

  get editable(): boolean {
    return (
      this.dataSource &&
      DataSourceUtils.instanceOfEditableDataSource(this.dataSource)
    );
  }

  get computedHeaders(): CrudHeader[] {
    return this.headers;
  }

  get isBurgerView(): boolean {
    return this.enableViewSwitcher ? this.activeView === "burger" : true;
  }

  get isColumnView(): boolean {
    return this.enableViewSwitcher && this.activeView === "column";
  }

  get paginationOptions(): number[] {
    const options = [5, 10, 20, 50];
    if (!options.includes(this.defaultPageSize)) {
      options.push(this.defaultPageSize);
      options.sort((a, b) => a - b);
    }
    return options;
  }

  mounted() {
    const urlSearch = (this.$route.query.search as string) || ''
    this.loading = true
    this.initialSearchCompleted = false 
    
    this.options.rows = this.defaultPageSize;
    
    if (urlSearch) {
      this.searchText = urlSearch;
    }
    this.options.page = 0;
    this.options.first = 0;
    this.find();
  }

  updateUrlSearchParam(value: string) {
    const newQuery = { ...this.$route.query };
    if (value) {
      newQuery.search = value;
    } else {
      delete newQuery.search;
    }
    this.$router.replace({ query: newQuery });
  }

  @Watch("search", { immediate: true })
  onSearchPropChange(newVal: string) {
    this.searchText = newVal;
    this.options.page = 0;
    this.options.first = 0;
    this.find();
  }

  @Emit("update:search")
  emitSearchUpdate(val: string): string {
    return val;
  }
  @Emit()
  addItem(): void {}

  @Emit()
  editItem(item: Identifiable<string>): Identifiable<string> {
    return { ...item };
  }

  @Emit()
  onRowClick(event: {
    data: Identifiable<string>;
    index: number;
  }): Identifiable<string> {
    return { ...event.data };
  }
  @Watch("searchText")
  onSearchTextChanged(newVal: string) {
    this.emitSearchUpdate(newVal);

    if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer);
    this.searchDebounceTimer = setTimeout(() => {
      this.options.page = 0;
      this.options.first = 0;
      this.find();
    }, 400);
  }
  onDataTablePage(event: DataTablePageEvent) {
    this.options.page = event.page;
    this.options.rows = event.rows;
    this.options.first = event.first;
    this.find();
  }

  onPaginatorPage(event: PageState) {
    this.options.page = event.page;
    this.options.rows = event.rows;
    this.options.first = event.first;
    this.find();
  }

  beforeUnmount() {
    if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer);
  }

  onSearchChange() {
    if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer);
    this.searchDebounceTimer = setTimeout(() => {
      this.options.page = 0;
      this.options.first = 0;
      this.find();
    }, 400);
  }

  handleCardClick(item: Identifiable<string>, index: number) {
    this.onRowClick({ data: item, index });
  }

  find() {
    if (!this.loading && this.dataSource) {
      this.loading = true;
    }

    const orders: Order[] = [];
    if (this.options.sortField) {
      orders.push(
        new Order(
          this.options.sortField,
          this.options.sortOrder === -1 ? Direction.DESC : Direction.ASC
        )
      );
    }

    const pageable = Pageable.create(this.options.page, this.options.rows, {
      orders,
    });
    const queryPromise: Promise<Page<Identifiable<string>>> = this.searchText
      ? this.dataSource.search(this.searchText, pageable)
      : this.dataSource.findAll(pageable);

    queryPromise
      .then((page: Page<Identifiable<string>>) => {
        this.loading = false;
        this.totalItems = page.totalElements ?? 0;
        this.items = page.content ?? [];
        this.initialSearchCompleted = true;

        this.$emit("items-count", this.items.length);
      })

      .catch((error: unknown) => {
        console.error("[CRUD Table Alert]:", error);
        this.loading = false;
        this.initialSearchCompleted = true;
      });
  }

  displayAlert(text: string) {
    this.toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 3000
    });
  }
}

export default toNative(CrudTable);
</script>

<template>
  <div :style="{ '--row-hover-color': rowHoverColor }">
    <Toolbar class="!border-none !px-0 !mb-6 !py-0">
      <template #start>
        <IconField class="max-w-sm">
          <InputIcon class="pi pi-search" />
          <InputText
            v-model="searchText"
            placeholder="Search"
            size="small"
            @input="onSearchChange"
            @keyup.enter="find"
          />
        </IconField>
      </template>

      <template #end>
        <div class="flex items-center gap-2 h-[33px]">
          <SelectButton
            size="small"
            v-if="enableViewSwitcher"
            v-model="activeView"
            :options="viewOptions"
            optionValue="value"
            dataKey="value"
          >
            <template #option="slotProps">
              <i :class="slotProps.option.icon"></i>
            </template>
          </SelectButton>
          <Button
            size="small"
            v-if="!disableModifications && isShowAddNew"
            @click="addItem"
            :label="createNewButtonText"
            icon="pi pi-plus"
          />
        </div>
      </template>
    </Toolbar>

    <div class="mb-6">
      <div v-if="isColumnView">
        <div
          v-if="items.length > 0"
          class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
        >
          <Card
            v-for="(item, index) in items"
            :key="item.id || index"
            class="cursor-pointer relative hover:shadow-md transition-shadow h-[170px] flex flex-col justify-between"
            @click="handleCardClick(item, index)"
          >
            <template #title>
              <h3 class="">{{ item?.id }}</h3>
            </template>

            <template #content>
              <p class="truncate-multiline max-h-[46px]">
                {{ item?.description }}
              </p>
            </template>

            <template #footer>
              <div class="flex p-5 gap-4 absolute bottom-0 left-0">
                <Button
                  severity="secondary"
                  text
                  class="!p-0"
                  @click.stop="
                    $router.push({
                      path: '/graphql',
                      query: { namespace: item.id },
                    })
                  "
                >
                  <img
                    src="@/assets/graphql.svg"
                    alt="GraphQL"
                    class="w-5 h-5"
                  />
                </Button>
                <Button
                  severity="secondary"
                  text
                  class="!p-0"
                  @click.stop="
                    $router.push('/scalar-ui.html?namespace=' + item.id)
                  "
                >
                  <img
                    src="@/assets/scalar.svg"
                    alt="OpenAPI"
                    class="w-5 h-5"
                  />
                </Button>
              </div>
            </template>
          </Card>
        </div>
        <div
          v-else
          class="flex flex-col items-center justify-center text-gray-500 py-20 h-[calc(100vh-300px)]"
        >
          <p class="text-sm">{{ emptyStateText }}</p>
        </div>

        <Paginator
          :rows="options.rows"
          :totalRecords="totalItems"
          :rowsPerPageOptions="paginationOptions"
          @page="onPaginatorPage"
          class="mt-4"
          v-if="showPagination"
        />
      </div>

      <div
        v-if="isBurgerView"
        class="p-4 border text-[color:var(--surface-200)] rounded-xl"
      >
        <DataTable
          :value="items"
          :rows="options.rows"
          :totalRecords="totalItems"
          :loading="loading"
          :paginator="showPagination"
          :first="options.first"
          :rowsPerPageOptions="paginationOptions"
          dataKey="id"
          @page="onDataTablePage"
          @row-click="onRowClick"
          sortMode="multiple"
          :rowClass="getRowClass"
        >
          <Column
            v-for="col in computedHeaders"
            :key="col.field"
            :field="col.field"
            :header="col.header"
            :sortable="col.sortable !== false"
            :headerStyle="
              col.centered ? { display: 'flex', justifyContent: 'center' } : {}
            "
          >
            <template #body="slotProps">
              <slot :name="`item.${col.field}`" :item="slotProps.data">
                {{ slotProps.data[col.field] }}
              </slot>
            </template>
          </Column>

          <Column v-if="editable || $slots['additional-actions']" header="">
            <template #body="slotProps">
              <div class="flex justify-center">
                <slot name="additional-actions" :item="slotProps.data" />
              </div>
            </template>
          </Column>
          <template #loading>
            <div
              class="flex justify-center bg-white h-full items-center py-20 text-gray-500 w-full"
            >
              <i class="pi pi-spin pi-spinner text-2xl text-primary" />
            </div>
          </template>
          <template #empty>
            <div
              class="flex justify-center items-center text-gray-500 py-8 h-[calc(100vh-450px)] w-full"
            >
              {{ emptyStateText }}
            </div>
          </template>
        </DataTable>
      </div>
    </div>

    <ConfirmDialog />
  </div>
</template>

<style>
.p-datatable-paginator-bottom {
  border: none !important;
  box-shadow: none !important;
}

.truncate-multiline {
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dynamic-hover:hover {
  cursor: pointer;
  background-color: var(--row-hover-color, #eff6ff) !important;
  transition: background-color 0.3s ease !important;
}
</style>
