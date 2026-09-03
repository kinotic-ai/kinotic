<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";

import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Button from "primevue/button";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import ConfirmDialog from "primevue/confirmdialog";
import Card from "primevue/card";
import Menu from "primevue/menu";
import type { MenuItem } from "primevue/menuitem";
import type { DataTableSortMeta } from "primevue/datatable";
import Paginator, { type PageState } from "primevue/paginator";
import SelectButton from "primevue/selectbutton";
import Skeleton from "primevue/skeleton";
import { useToast } from "primevue/usetoast";
import { useConfirm } from "primevue/useconfirm";

import {
  type IDataSource,
  type Identifiable,
  Order,
  type Page,
  Pageable,
  Direction,
} from "@kinotic-ai/core";

import type { CrudHeader } from "../types/CrudHeader";
import type { DescriptiveIdentifiable } from "../types/DescriptiveIdentifiable";
import { createDebug } from "../util/debug";
import { isDark as darkMode } from '../composables/useTheme'

const debug = createDebug('crud-table');

/** Wide enough for the ellipsis button plus the body cell padding. */
const ROW_MENU_COLUMN_WIDTH = '4.5rem';

const props = withDefaults(defineProps<{
  // any: parents bind entity-specific IDataSource implementations (EntityDefinition,
  // Dashboard, ...) and IDataSource's type parameter is invariant.
  dataSource: IDataSource<any>
  headers: CrudHeader[]
  multiSort?: boolean
  mustSort?: boolean
  singleExpand?: boolean
  disableModifications?: boolean
  isShowAddNew?: boolean
  initialSearch?: string
  rowHoverColor?: string
  createNewButtonText?: string
  enableViewSwitcher?: boolean
  emptyStateText?: string
  search?: string
  showPagination?: boolean
  enableRowHover?: boolean
  defaultPageSize?: number
  transparentDarkCards?: boolean
  // The sort the table opens with, applied by the data source. The user replaces it by
  // clicking a column header.
  defaultSort?: Order[]
  // Fills the first load with placeholder rows. Worth it only where the fetch is slow
  // enough to be worth previewing — against a fast one the placeholder is a flash.
  showLoadingSkeleton?: boolean
  // When set, each row gets an ellipsis button opening a popup menu with these
  // items. Rows for which the function returns an empty array get no button.
  rowActions?: (item: any) => MenuItem[]
  // Appends a Delete item to the row menu that confirms with the user, then
  // emits deleteItem. The parent performs the actual deletion.
  isShowDelete?: boolean
}>(), {
  multiSort: false,
  mustSort: true,
  singleExpand: false,
  disableModifications: false,
  isShowAddNew: true,
  isShowDelete: false,
  initialSearch: '',
  rowHoverColor: '#f5f5f5',
  createNewButtonText: 'Add new',
  enableViewSwitcher: false,
  emptyStateText: 'No items yet',
  search: '',
  showPagination: true,
  enableRowHover: true,
  defaultPageSize: 10,
  transparentDarkCards: false,
  showLoadingSkeleton: false,
});

const emit = defineEmits<{
  (e: "update:search", value: string): void;
  (e: "addItem"): void;
  // any: listeners type these payloads as their concrete entity (EntityDefinition,
  // Dashboard, ...), which is narrower than the Identifiable<string> rows the table holds.
  (e: "deleteItem", item: any): void;
  (e: "onRowClick", data: any): void;
  (e: "items-count", count: number): void;
}>();

const toast = useToast()
const route = useRoute()

function getRowClass() {
  return {
    "dynamic-hover": props.enableRowHover && !showSkeleton.value,
    "transition-all": true,
  };
}

const items = ref<DescriptiveIdentifiable[]>([]);
const totalItems = ref(0);
const loading = ref(false);
const initialSearchCompleted = ref(false);
const searchDebounceTimer = ref<ReturnType<typeof setTimeout> | null>(null);
const activeView = ref<"burger" | "column">("burger");
const searchText = ref<string | null>("");
const options = ref({
  page: 0,
  rows: 10,
  first: 0,
});

// The DataTable is lazy, so it does not reorder the rows it was given. This is the order it
// displays and the order find() asks the data source for, seeded from defaultSort.
const sortMeta = ref<DataTableSortMeta[]>(
  (props.defaultSort ?? []).map((order) => ({
    field: order.property,
    order: order.direction === Direction.DESC ? -1 : 1,
  }))
);

const viewOptions = [
  { icon: "pi pi-bars", value: "burger" },
  { icon: "pi pi-th-large", value: "column" },
];

const rowMenus = ref<Record<string, any>>({});
const confirm = useConfirm();

function toggleRowMenu(event: Event, itemId: string): void {
  rowMenus.value[itemId]?.toggle(event);
}

const hasRowMenu = computed<boolean>(() => {
  return !!props.rowActions || props.isShowDelete;
});

/** True while a fetch is in flight and there are no rows to keep on screen. */
const isInitialLoad = computed<boolean>(() => {
  return loading.value && items.value.length === 0;
});

const showSkeleton = computed<boolean>(() => {
  return props.showLoadingSkeleton && isInitialLoad.value;
});

// One placeholder per row the page holds, so the loading state occupies the same height a
// full page of rows will.
const skeletonRows = computed<DescriptiveIdentifiable[]>(() => {
  return Array.from({ length: options.value.rows }, (_, index) => ({ id: `skeleton-${index}` }));
});

/** What the table and the card grid iterate: the loaded rows, or placeholders while they load. */
const displayRows = computed<DescriptiveIdentifiable[]>(() => {
  return showSkeleton.value ? skeletonRows.value : items.value;
});

// The paginator disables itself at a count of zero, so a real count would switch it out of
// its greyed-out state on arrival. Standing in a full page keeps it enabled throughout.
const displayTotal = computed<number>(() => {
  return isInitialLoad.value ? options.value.rows : totalItems.value;
});

function rowMenuItems(item: DescriptiveIdentifiable): MenuItem[] {
  const menuItems = props.rowActions ? [...props.rowActions(item)] : [];
  if (props.isShowDelete) {
    menuItems.push({
      label: "Delete",
      icon: "pi pi-trash",
      command: () => confirmDelete(item),
    });
  }
  return menuItems;
}

function confirmDelete(item: DescriptiveIdentifiable): void {
  confirm.require({
    header: "Confirm delete",
    message: `Permanently delete ${item.name ?? "this item"}? This cannot be undone.`,
    icon: "pi pi-exclamation-triangle",
    acceptProps: { label: "Delete", severity: "danger" },
    rejectProps: { label: "Cancel", severity: "secondary", outlined: true },
    accept: () => emit("deleteItem", item),
  });
}

const computedHeaders = computed<CrudHeader[]>(() => {
  return props.headers;
});

const isBurgerView = computed<boolean>(() => {
  return props.enableViewSwitcher ? activeView.value === "burger" : true;
});

const isColumnView = computed<boolean>(() => {
  return props.enableViewSwitcher && activeView.value === "column";
});

const paginationOptions = computed<number[]>(() => {
  const options = [5, 10, 20, 50];
  if (!options.includes(props.defaultPageSize)) {
    options.push(props.defaultPageSize);
    options.sort((a, b) => a - b);
  }
  return options;
});

const isDark = darkMode;

const dataTablePt = computed(() => {
  return {
    root: {
      class: 'bg-transparent'
    },
    tableContainer: {
      class: 'bg-transparent'
    },
    // table-fixed keeps the columns sized from the header widths alone. Under the default
    // auto layout the browser measures the loaded rows, so every column jumps once the
    // first page arrives — the headers visibly reflow.
    table: {
      class: 'bg-transparent border-separate border-spacing-0 table-fixed'
    },
    header: {
      class: 'hidden'
    },
    // The header row is sticky over the scrolling rows, so it carries the surface colour the
    // shell sits on rather than being transparent — otherwise rows show through it.
    headerCell: {
      class: [
        'px-[14px] pb-[0.9rem] pt-4 text-sm font-semibold',
        isDark.value ? 'bg-surface-900 border-surface-700 text-surface-100' : 'bg-surface-0 border-surface-200 text-surface-950'
      ]
    },
    bodyRow: {
      class: [
        'bg-transparent',
        isDark.value ? 'border-surface-800 text-surface-200' : 'border-surface-100 text-surface-950'
      ]
    },
    bodyCell: {
      class: [
        'bg-transparent px-[14px] py-2 text-sm align-middle',
        isDark.value ? 'border-surface-700 text-surface-200' : 'border-surface-200 text-surface-950'
      ]
    },
    // The empty state renders outside the DataTable (in the flex filler below it),
    // so suppress the built-in empty-message row.
    emptyMessage: {
      class: 'hidden'
    }
  };
});

onMounted(() => {
  const urlSearch = (route.query.search as string) || ''
  loading.value = true
  initialSearchCompleted.value = false

  options.value.rows = props.defaultPageSize;

  if (urlSearch) {
    searchText.value = urlSearch;
  }
  reloadFirstPage();
});

watch(
  () => props.search,
  (newVal) => {
    // Parents that two-way bind echo every update:search emit back into this prop;
    // without this guard each keystroke triggers an immediate find() on top of the
    // debounced one from the searchText watch.
    if (newVal === searchText.value) {
      return;
    }
    searchText.value = newVal;
    reloadFirstPage();
  },
  { immediate: true }
);

function emitSearchUpdate(val: string) {
  emit("update:search", val);
}

function addItem() {
  emit("addItem");
}

function onRowClick(event: {
  data: Identifiable<string>;
  index: number;
}) {
  if (showSkeleton.value) {
    return;
  }
  emit("onRowClick", { ...event.data });
}

watch(searchText, (newVal) => {
  emitSearchUpdate(newVal as string);

  if (searchDebounceTimer.value) clearTimeout(searchDebounceTimer.value);
  searchDebounceTimer.value = setTimeout(reloadFirstPage, 400);
});

/** Refetches from the first page, which every change to what the query selects starts over at. */
function reloadFirstPage() {
  options.value.page = 0;
  options.value.first = 0;
  find();
}

function onPaginatorPage(event: PageState) {
  options.value.page = event.page;
  options.value.rows = event.rows;
  options.value.first = event.first;
  find();
}

onBeforeUnmount(() => {
  if (searchDebounceTimer.value) clearTimeout(searchDebounceTimer.value);
});

function onSearchChange() {
  if (searchDebounceTimer.value) clearTimeout(searchDebounceTimer.value);
  searchDebounceTimer.value = setTimeout(reloadFirstPage, 400);
}

function handleCardClick(item: Identifiable<string>, index: number) {
  onRowClick({ data: item, index });
}

function find() {
  if (!loading.value && props.dataSource) {
    loading.value = true;
  }

  const orders: Order[] = sortMeta.value.map(
    (meta) => new Order(String(meta.field), meta.order === -1 ? Direction.DESC : Direction.ASC)
  );

  const pageable = Pageable.create(options.value.page, options.value.rows, {
    orders,
  });
  const queryPromise: Promise<Page<Identifiable<string>>> = searchText.value
    ? props.dataSource.search(searchText.value, pageable)
    : props.dataSource.findAll(pageable);

  queryPromise
    .then((page: Page<Identifiable<string>>) => {
      loading.value = false;
      totalItems.value = page.totalElements ?? 0;
      items.value = page.content ?? [];
      initialSearchCompleted.value = true;

      emit("items-count", items.value.length);
    })

    .catch((error: unknown) => {
      debug('Error loading data: %O', error);
      loading.value = false;
      initialSearchCompleted.value = true;
    });
}

function displayAlert(text: string) {
  toast.add({
    severity: 'error',
    summary: 'Error',
    detail: text,
    life: 3000
  });
}

// displayAlert is exposed because ProjectEntityDefinitionsTable and EntityDefinitionsList
// surface publish errors through their crudTable ref.
defineExpose({ find, displayAlert });
</script>

<template>
  <!-- flex-1 lets the table fill the remaining height when a page provides a flex column
       chain down to here; in a plain block parent the flex classes are inert. min-h-0 runs
       down that chain so the rows scroll inside the shell instead of stretching it past the
       viewport, which would carry the paginator off screen. -->
  <div class="crud-table flex min-h-0 flex-1 flex-col" :class="isDark ? 'crud-table--dark' : 'crud-table--light'" :style="{ '--row-hover-color': rowHoverColor }">
    <div class="crud-table__toolbar flex items-center justify-between mb-6 gap-4">
      <IconField class="crud-table__search w-[236px] max-w-sm">
        <InputIcon class="pi pi-search" />
        <InputText
          v-model="searchText"
          placeholder="Search"
          size="small"
          name="search"
          autocomplete="off"
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

    <div class="mb-6 flex min-h-0 flex-1 flex-col">
      <div v-if="isColumnView" class="flex min-h-0 flex-1 flex-col">
        <div
          v-if="displayRows.length > 0"
          class="grid min-h-0 flex-1 auto-rows-min grid-cols-1 gap-4 overflow-y-auto sm:grid-cols-2 lg:grid-cols-3"
        >
          <Card
            v-for="(item, index) in displayRows"
            :key="item.id || index"
            :class="[
              'relative flex h-[170px] flex-col justify-between border transition-shadow',
              showSkeleton ? '' : 'cursor-pointer',
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
              <Skeleton v-if="showSkeleton" height="1.25rem" width="55%" />
              <h3 v-else :class="isDark ? 'text-surface-0 font-semibold' : ''">{{ item?.id }}</h3>
            </template>

            <template #content>
              <Skeleton v-if="showSkeleton" height="0.875rem" width="85%" />
              <p v-else :class="['max-h-[46px] overflow-hidden text-sm [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:2]', isDark ? 'text-surface-400' : 'text-surface-500']">
                {{ item?.description }}
              </p>
            </template>

            <template #footer>
              <div v-if="!showSkeleton" class="flex p-5 gap-4 absolute bottom-0 left-0">
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
                    src="../assets/graphql.svg"
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
                    src="../assets/scalar.svg"
                    alt="OpenAPI"
                    class="w-5 h-5"
                  />
                </Button>
              </div>
            </template>
          </Card>
        </div>
        <div
          v-else-if="!loading"
          :class="['flex flex-1 flex-col items-center justify-center py-20', isDark ? 'text-surface-400' : 'text-surface-500']"
        >
          <p class="text-sm">{{ emptyStateText }}</p>
        </div>

        <Paginator
          :rows="options.rows"
          :totalRecords="displayTotal"
          :rowsPerPageOptions="paginationOptions"
          @page="onPaginatorPage"
          class="mt-auto pt-4"
          v-if="showPagination"
        />
      </div>

      <div v-if="isBurgerView" class="flex min-h-0 flex-1 flex-col">
        <div
          :class="[
            'crud-table__table-shell flex min-h-0 flex-1 flex-col rounded-[14px] border px-4 py-2 transition-colors',
            isDark ? 'border-surface-700 bg-transparent text-surface-0 shadow-[0_0_0_1px_rgba(58,58,64,0.15)]' : 'border-surface-200 bg-transparent text-surface-950'
          ]"
        >
          <DataTable
            :class="[
              'crud-table__datatable',
              { 'crud-table__datatable--loading': loading },
              displayRows.length > 0 ? 'min-h-0 flex-1' : ''
            ]"
            :pt="dataTablePt"
            :value="displayRows"
            dataKey="id"
            scrollable
            scrollHeight="flex"
            @row-click="onRowClick"
            lazy
            sortMode="multiple"
            v-model:multiSortMeta="sortMeta"
            @sort="reloadFirstPage"
            :rowClass="getRowClass"
          >
            <Column
              v-for="col in computedHeaders"
              :key="col.field"
              :field="col.field"
              :header="col.header"
              :sortable="col.sortable !== false"
              :style="{ width: col.width }"
              :headerStyle="col.centered ? { textAlign: 'center' } : {}"
            >
              <template #body="slotProps">
                <div :class="['flex min-h-[48px] items-center', col.centered ? 'w-full justify-center' : '']">
                  <Skeleton v-if="showSkeleton" height="0.875rem" width="60%" />
                  <!-- min-w-0 lets this shrink below its min-content width, so a long
                       unbreakable value wraps inside the column instead of spilling into
                       the next one. Wrapping the slot rather than the flex row itself keeps
                       badges and buttons at their natural width. -->
                  <div v-else class="min-w-0 break-words">
                    <slot :name="`item.${col.field}`" :item="slotProps.data">
                      {{ slotProps.data[col.field] }}
                    </slot>
                  </div>
                </div>
              </template>
            </Column>

            <Column v-if="hasRowMenu" header="" :style="{ width: ROW_MENU_COLUMN_WIDTH }">
              <template #body="slotProps">
                <div class="flex min-h-[48px] w-full items-center justify-center">
                  <Skeleton v-if="showSkeleton" shape="circle" size="1.25rem" />
                  <template v-else-if="rowMenuItems(slotProps.data).length > 0">
                    <Button
                      icon="pi pi-ellipsis-v"
                      @click.stop="(event) => toggleRowMenu(event, slotProps.data.id)"
                      aria-haspopup="true"
                      :aria-controls="'action_menu_' + slotProps.data.id"
                      type="button"
                      severity="secondary"
                      variant="text"
                    />
                    <Menu
                      :ref="(el) => (rowMenus[slotProps.data.id] = el)"
                      :model="rowMenuItems(slotProps.data)"
                      :popup="true"
                      :id="'action_menu_' + slotProps.data.id"
                    />
                  </template>
                </div>
              </template>
            </Column>
          </DataTable>

          <!-- Centered in the space under the header row, which the table leaves free only
               when it has no rows to scroll. -->
          <div
            v-if="!loading && items.length === 0"
            :class="['flex flex-1 items-center justify-center', isDark ? 'text-surface-400' : 'text-surface-500']"
          >
            <span class="py-20">{{ emptyStateText }}</span>
          </div>
        </div>

        <Paginator
          v-if="showPagination"
          :rows="options.rows"
          :first="options.first"
          :totalRecords="displayTotal"
          :rowsPerPageOptions="paginationOptions"
          @page="onPaginatorPage"
          class="border-0 bg-transparent px-0 pb-[0.875rem] pt-3 shadow-none"
        />
      </div>
    </div>

    <ConfirmDialog />
  </div>
</template>

<style>
/* While loading, an indeterminate line overlays the header row's bottom divider. */
.crud-table__datatable--loading .p-datatable-thead {
  position: relative;
}

.crud-table__datatable--loading .p-datatable-thead::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--p-primary-500), transparent);
  background-size: 40% 100%;
  background-repeat: no-repeat;
  animation: crud-table-loading-slide 1.2s ease-in-out infinite;
}

@keyframes crud-table-loading-slide {
  0% {
    background-position: -100% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.p-datatable-paginator-bottom {
  border: none !important;
  box-shadow: none !important;
}

/* Anchors the rows-per-page select to the table's right edge, so the page buttons grow
   leftward as the record count arrives rather than pushing the select sideways. Two class
   selectors to outweigh the theme's own centring on .p-paginator. */
.crud-table .p-paginator {
  justify-content: flex-end;
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
