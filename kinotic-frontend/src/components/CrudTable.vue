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
} from "@kinotic-ai/core";

import type { CrudHeader } from "@/types/CrudHeader";
import type { DescriptiveIdentifiable } from "@/types/DescriptiveIdentifiable";
import { createDebug } from "@/util/debug";
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('crud-table');

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
  @Prop({ default: false }) transparentDarkCards!: boolean

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

  get isDark(): boolean {
    return darkMode.value;
  }
  
  get dataTablePt() {
    return {
      root: {
        class: 'bg-transparent'
      },
      tableContainer: {
        class: 'bg-transparent'
      },
      table: {
        class: 'bg-transparent border-separate border-spacing-0'
      },
      header: {
        class: 'hidden'
      },
      headerCell: {
        class: [
          'bg-transparent px-[14px] pb-[0.9rem] pt-4 text-sm font-semibold',
          this.isDark ? 'border-surface-700 text-surface-100' : 'border-surface-200 text-surface-950'
        ]
      },
      bodyRow: {
        class: [
          'bg-transparent',
          this.isDark ? 'border-surface-800 text-surface-200' : 'border-surface-100 text-surface-950'
        ]
      },
      bodyCell: {
        class: [
          'bg-transparent px-[14px] py-4 text-sm align-middle',
          this.isDark ? 'border-surface-700 text-surface-200' : 'border-surface-200 text-surface-950'
        ]
      },
      pcPaginator: {
        root: {
          class: 'justify-end border-0 bg-transparent px-0 pb-[0.875rem] pt-3 shadow-none'
        }
      }
    };
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
        debug('Error loading data: %O', error);
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
  <div class="crud-table" :class="isDark ? 'crud-table--dark' : 'crud-table--light'" :style="{ '--row-hover-color': rowHoverColor }">
    <div class="crud-table__toolbar flex items-center justify-between mb-6 gap-4">
      <IconField class="crud-table__search w-[236px] max-w-sm">
        <InputIcon class="pi pi-search" />
        <InputText
          v-model="searchText"
          :class="[
            '!shadow-none',
            isDark
              ? 'border-surface-700 bg-surface-950 text-surface-0 placeholder:text-surface-500'
              : 'border-surface-300 bg-surface-0 text-surface-950 placeholder:text-surface-400'
          ]"
          placeholder="Search"
          size="small"
          @input="onSearchChange"
          @keyup.enter="find"
        />
      </IconField>

      <div class="crud-table__actions flex items-center gap-2 h-[36px]">
        <SelectButton
          class="crud-table__view-switcher"
          size="small"
          v-if="enableViewSwitcher"
          v-model="activeView"
          :options="viewOptions"
          optionLabel="value"
          optionValue="value"
          dataKey="value"
        >
          <template #option="slotProps">
            <i :class="slotProps.option.icon"></i>
          </template>
        </SelectButton>
        <Button
          :class="[
            '!border-transparent !shadow-none',
            isDark
              ? 'hover:!bg-primary-600'
              : 'hover:!bg-primary-600'
          ]"
          size="small"
          v-if="!disableModifications && isShowAddNew"
          @click="addItem"
          :label="createNewButtonText"
          icon="pi pi-plus"
        />
      </div>
    </div>

    <div class="mb-6">
      <div v-if="isColumnView">
        <div
          v-if="items.length > 0"
          class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
        >
          <Card
            v-for="(item, index) in items"
            :key="item.id || index"
            :class="[
              'relative flex h-[170px] cursor-pointer flex-col justify-between border transition-shadow',
              isDark
                ? [
                    transparentDarkCards ? 'border-surface-700 bg-transparent text-surface-0 shadow-none' : 'border-surface-700 bg-surface-900 text-surface-0 shadow-none',
                    'hover:shadow-[0_8px_28px_rgba(0,0,0,0.35)]'
                  ]
                : 'border-surface-200 bg-surface-0 text-surface-950 hover:shadow-md'
            ]"
            @click="handleCardClick(item, index)"
          >
            <template #title>
              <h3 :class="isDark ? 'text-surface-0 font-semibold' : ''">{{ item?.id }}</h3>
            </template>

            <template #content>
              <p :class="['max-h-[46px] overflow-hidden text-sm [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:2]', isDark ? 'text-surface-400' : 'text-surface-500']">
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
          :class="['flex flex-col items-center justify-center py-20 h-[calc(100vh-300px)]', isDark ? 'text-surface-400' : 'text-surface-500']"
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
        :class="[
          'crud-table__table-shell rounded-[14px] border px-4 pt-2 pb-0 transition-colors',
          isDark ? 'border-surface-700 bg-transparent text-surface-0 shadow-[0_0_0_1px_rgba(58,58,64,0.15)]' : 'border-surface-200 bg-transparent text-surface-950'
        ]"
      >
        <DataTable
          class="crud-table__datatable"
          :pt="dataTablePt"
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
            :headerStyle="col.centered ? { textAlign: 'center' } : {}"
          >
            <template #body="slotProps">
              <div
                v-if="col.centered"
                class="flex items-center justify-center w-full min-h-[64px]"
              >
                <slot :name="`item.${col.field}`" :item="slotProps.data">
                  {{ slotProps.data[col.field] }}
                </slot>
              </div>
              <template v-else>
                <div class="flex min-h-[64px] items-center">
                  <slot :name="`item.${col.field}`" :item="slotProps.data">
                    {{ slotProps.data[col.field] }}
                  </slot>
                </div>
              </template>
            </template>
          </Column>

          <Column v-if="editable || $slots['additional-actions']" header="">
            <template #body="slotProps">
              <div class="flex min-h-[64px] w-full items-center justify-center">
                <slot name="additional-actions" :item="slotProps.data" />
              </div>
            </template>
          </Column>
          <template #loading>
            <div
              :class="['flex h-full w-full items-center justify-center py-20', isDark ? 'bg-transparent text-surface-400' : 'bg-transparent text-surface-500']"
            >
              <i class="pi pi-spin pi-spinner text-2xl text-primary" />
            </div>
          </template>
          <template #empty>
            <div
              :class="['flex h-[calc(100vh-450px)] w-full items-center justify-center py-8', isDark ? 'text-surface-400' : 'text-surface-500']"
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

.crud-table--light .crud-table__view-switcher.p-selectbutton {
  border-radius: 0.625rem;
  border: 1px solid var(--p-surface-200);
  background: var(--p-surface-50);
}

.crud-table--light .crud-table__view-switcher .p-togglebutton {
  border: none;
  background: transparent;
  color: var(--p-surface-500);
}

.crud-table--light .crud-table__view-switcher .p-togglebutton.p-togglebutton-checked {
  background: var(--p-surface-0);
  color: var(--p-surface-950);
}

.crud-table--light .crud-table__add-button.p-button {
  border: none;
  background: var(--p-primary-500);
  color: var(--p-surface-0);
  box-shadow: none;
}

.crud-table--light .crud-table__add-button.p-button:hover {
  background: var(--p-primary-600);
}

html.dark .p-selectbutton {
  border-radius: 0.625rem;
  border: 1px solid var(--p-surface-700);
  background: var(--p-surface-900);
}

html.dark .p-selectbutton .p-togglebutton {
  border: none;
  background: transparent;
  color: var(--p-surface-400);
}

html.dark .p-selectbutton .p-togglebutton.p-togglebutton-checked {
  background: var(--p-surface-800);
  color: var(--p-surface-0);
}

html.dark .crud-table .p-button {
  border-color: transparent;
}

html.dark .crud-table .p-button.p-button-sm:not(.p-button-text):not(.p-selectbutton-button) {
  background: var(--p-primary-500);
  color: var(--p-surface-0);
}

html.dark .crud-table .p-button.p-button-sm:not(.p-button-text):not(.p-selectbutton-button):hover {
  background: var(--p-primary-600);
}

html.dark .p-paginator .p-paginator-page,
html.dark .p-paginator .p-paginator-next,
html.dark .p-paginator .p-paginator-prev,
html.dark .p-paginator .p-paginator-first,
html.dark .p-paginator .p-paginator-last {
  color: var(--p-surface-300) !important;
}

.dynamic-hover:hover {
  cursor: pointer;
  background-color: var(--row-hover-color, #eff6ff) !important;
  transition: background-color 0.3s ease !important;
}

html.dark .dynamic-hover:hover {
  background-color: var(--p-surface-800) !important;
}
</style>
