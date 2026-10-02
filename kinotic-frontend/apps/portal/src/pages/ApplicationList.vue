<script setup lang="ts">
import { CrudTable } from "@kinotic-ai/frontend-common";
import ApplicationSidebar from "@/components/ApplicationSidebar.vue";
import ApplicationProjectsLink from "@/components/ApplicationProjectsLink.vue";
import DeleteApplicationDialog from "@/components/DeleteApplicationDialog.vue";
import { InitialsTile, PageHeader, TimePill } from "@kinotic-ai/frontend-common";
import { Kinotic } from "@kinotic-ai/core";
import {
  type IApplicationService,
  type Application,
} from "@kinotic-ai/management-api";
import { APPLICATION_STATE } from "@/states/IApplicationState";
import type { CrudHeader } from "@kinotic-ai/frontend-common";
import type { Identifiable } from "@kinotic-ai/core";
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { createDebug } from "@kinotic-ai/frontend-common";
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const debug = createDebug('application-list');

const route = useRoute();
const router = useRouter();

const headers: CrudHeader[] = [
  { field: "name", header: "Name", sortable: false, width: "22%" },
  { field: "id", header: "Id", sortable: false, width: "20%", optional: true },
  { field: "description", header: "Description", sortable: false, width: "32%", optional: true },
  { field: "projects", header: "Projects", sortable: false, width: "12%" },
  { field: "updated", header: "Updated", sortable: false, width: "14%" },
];

const dataSource: IApplicationService = Kinotic.applications;
const showSidebar = ref(false);
const searchText = ref<string>((route.query.search as string) || "");
const crudTable = ref<InstanceType<typeof CrudTable>>();

onMounted(async () => {
  try {
    refreshTable();

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
  showSidebar.value = false;
  // An application is only useful once it has a project, so carry straight on to creating one
  router.push(`/application/${encodeURIComponent(created.id)}/projects?openNewProject=1`);
}

const applicationToDelete = ref<Application | null>(null);

// A failed delete may have removed some of the application's projects, so the counts refresh
function onDeleteDialogClose(): void {
  applicationToDelete.value = null;
  refreshTable();
}

function onApplicationDeleted(deleted: Application): void {
  applicationToDelete.value = null;
  APPLICATION_STATE.allApplications = APPLICATION_STATE.allApplications.filter(
    (a) => a.id !== deleted.id
  );
  refreshTable();
}
</script>

<template>
  <div :class="['application-list flex flex-col transition-colors', isDark ? 'application-list--dark text-surface-0' : 'text-surface-950']">
    <PageHeader title="Applications"
                description="The applications your organization builds and operates on Kinotic." />
    <CrudTable
      ref="crudTable"
      createNewButtonText="New application"
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
      @delete-item="applicationToDelete = $event"
      @onRowClick="toApplicationPage"
      class="application-list__table !text-sm"
    >
    <template #item.name="{ item, index }">
      <span class="flex min-w-0 items-center gap-2.5">
        <InitialsTile :name="item.name || item.id" :index="index" />
        <span class="truncate" v-tooltip.top="item.name">{{ item.name }}</span>
      </span>
    </template>
    <template #card.icon="{ item, index }">
      <InitialsTile :name="item.name || item.id || ''" :index="index" size="lg" />
    </template>
    <template #item.id="{ item }">
      <span>{{ item.id }}</span>
    </template>
    <template #item.description="{ item }">
      <!-- the right padding keeps the same gap before Projects as the Id column leaves before it -->
      <span class="block max-w-full truncate pr-10" v-tooltip.top="item.description || null">
        {{ item.description }}
      </span>
    </template>
    <template #item.projects="{ item }">
      <ApplicationProjectsLink :application-id="item.id" />
    </template>
    <template #item.updated="{ item }">
      <TimePill :date="item.updated" />
    </template>
    </CrudTable>

    <DeleteApplicationDialog
      :application="applicationToDelete"
      @deleted="onApplicationDeleted"
      @close="onDeleteDialogClose"
    />

    <ApplicationSidebar
      :visible="showSidebar"
      @close="onSidebarClose"
      @submit="onApplicationSubmit"
    />
  </div>
</template>
