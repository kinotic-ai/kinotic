<script setup lang="ts">
import { CrudTable } from "@kinotic-ai/frontend-common";
import ApplicationSidebar from "@/components/ApplicationSidebar.vue";
import { Kinotic } from "@kinotic-ai/core";
import {
  type IApplicationService,
  type Application,
} from "@kinotic-ai/os-api";
import { APPLICATION_STATE } from "@/states/IApplicationState";
import { onClickOutside } from "@vueuse/core";
import type { CrudHeader } from "@kinotic-ai/frontend-common";
import type { Identifiable } from "@kinotic-ai/core";
import { onMounted, ref, shallowRef, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import { showErrorToast } from "@kinotic-ai/frontend-common";
import { DatetimeUtil } from "@kinotic-ai/frontend-common";
import { createDebug } from "@kinotic-ai/frontend-common";
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const debug = createDebug('application-list');

const route = useRoute();
const router = useRouter();
const toast = useToast();

const headers: CrudHeader[] = [
  { field: "name", header: "Name", sortable: false, width: "22%" },
  { field: "id", header: "Id", sortable: false, width: "22%" },
  { field: "description", header: "Description", sortable: false, width: "32%" },
  { field: "created", header: "Created", sortable: false, width: "12%" },
  { field: "updated", header: "Updated", sortable: false, width: "12%" },
];

const dataSource: IApplicationService = Kinotic.applications;
const showSidebar = ref(false);
const searchText = ref<string>((route.query.search as string) || "");
const sidebarWrapper = ref<HTMLElement>();
const crudTable = ref<InstanceType<typeof CrudTable>>();

onMounted(async () => {
  try {
    refreshTable();
    const el = shallowRef<HTMLElement | null>(sidebarWrapper.value ?? null);

    onClickOutside(el, () => {
      if (showSidebar.value) {
        onSidebarClose();
      }
    });

    if (route.query.add === "true") {
      showSidebar.value = true;
      router.replace({ query: {} });
    }

    if (route.query.created === "true") {
      router.replace({ query: {} });
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    debug('Initialization error: %s', message);
  }
});

watch(() => route.query.search, (newVal) => {
  searchText.value = (newVal as string) || "";
});

function refreshTable(): void {
  crudTable.value?.find();
}
function updateRouteQuery(search: string) {
  const query = { ...route.query };

  if (search) {
    query.search = search;
  } else {
    delete query.search;
  }

  router.replace({ query });
}
const isDark = darkMode;

function onAddItem(): void {
  showSidebar.value = true;
}

async function toApplicationPage(item: Identifiable<string>): Promise<void> {
  try {
    const appId = item.id ?? "";
    const app = await dataSource.findById(appId);
    APPLICATION_STATE.currentApplication = app;
    router.push(`/application/${encodeURIComponent(appId)}`);
  } catch (e) {
    debug('Failed to navigate to application: %O', e);
  }
}

function onSidebarClose(): void {
  showSidebar.value = false;
}

function onApplicationSubmit(created: Application): void {
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
  refreshTable();
  showSidebar.value = false;
}

async function deleteApplication(item: Application): Promise<void> {
  try {
    await dataSource.deleteById(item.id!);
    toast.add({ severity: "success", summary: "Application deleted", life: 4000 });
    APPLICATION_STATE.allApplications = APPLICATION_STATE.allApplications.filter(
      (a) => a.id !== item.id
    );
    refreshTable();
  } catch (err) {
    showErrorToast(toast, "Failed to delete application", err, { life: 8000 });
  }
}
</script>

<template>
  <div :class="['application-list flex flex-col transition-colors', isDark ? 'application-list--dark text-surface-0' : 'text-surface-950']">
    <h1 :class="['mb-5 text-2xl font-semibold', isDark ? 'text-white' : 'text-surface-950']">Applications</h1>
    <CrudTable
      ref="crudTable"
      createNewButtonText="New application"
      rowHoverColor=""
      transparent-dark-cards
      :data-source="dataSource"
      :headers="headers"
      :singleExpand="false"
      :enableViewSwitcher="true"
      emptyStateText="No applications yet"
      :isShowDelete="true"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddItem"
      @delete-item="deleteApplication"
      @onRowClick="toApplicationPage"
      class="application-list__table !text-sm"
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
    </CrudTable>

    <div v-show="showSidebar" ref="sidebarWrapper">
      <ApplicationSidebar
        :visible="showSidebar"
        @close="onSidebarClose"
        @submit="onApplicationSubmit"
      />
    </div>
  </div>
</template>
