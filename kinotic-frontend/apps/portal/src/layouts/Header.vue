<template>
  <div ref="headerRef" class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-200 bg-surface-0 px-4 md:pl-[18px] md:pr-6 dark:border-surface-800 dark:bg-surface-900">
    <div class="relative flex min-w-0 items-center gap-3 text-surface-950 dark:text-surface-0">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-surface-600 transition-colors hover:text-surface-950 md:hidden dark:text-surface-300 dark:hover:text-surface-0"
        aria-label="Open navigation"
        @click="emit('toggle-nav')"
      >
        <span class="pi pi-bars"></span>
      </button>

      <!-- Wide enough that, after the 18px header padding and the 12px gap, the first "/" lands on
           the collapsed sidebar's right edge (73px); it stays there when the sidebar expands -->
      <div class="flex shrink-0 items-center md:w-[43px]">
        <!-- The green mark is drawn for a dark ground, so it sits on its own dark tile on the white header -->
        <RouterLink to="/applications" class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-surface-950 dark:ring-1 dark:ring-surface-700">
          <img src="@/assets/header-logo.svg" class="h-[18px] w-5" alt="Kinotic" />
        </RouterLink>
      </div>

      <!-- On small screens only the deepest segment stays; the sidebar's back row names the rest.
           On wider screens the separator takes no width so the "/" centres on the sidebar edge. -->
      <span :class="['text-lg text-surface-300 md:flex md:w-0 md:justify-center dark:text-surface-600', applicationId ? 'hidden' : '']">/</span>
      <div :class="['items-center gap-1', applicationId ? 'hidden md:flex' : 'flex']">
        <RouterLink to="/applications"
          class="flex items-center gap-1.5 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
          {{ organizationId }}
          <span class="text-[11px] font-normal text-surface-500">org</span>
        </RouterLink>
        <!-- Inside an application its own segment carries the switcher -->
        <BreadcrumbSwitcher
          v-if="!applicationId"
          :items="applicationItems"
          label="Switch application"
          search-placeholder="Find application…"
          all-label="All applications"
          all-to="/applications"
          create-label="New application"
          create-to="/applications?add=true"
          @select="selectApp"
        />
      </div>

      <template v-if="applicationId">
        <span :class="['text-lg text-surface-300 dark:text-surface-600', projectId ? 'hidden md:inline' : '']">/</span>
        <div :class="['items-center gap-1', projectId ? 'hidden md:flex' : 'flex']">
          <RouterLink :to="applicationPath"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
            {{ applicationId }}
            <ScopePill kind="application" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="applicationItems"
            :current-id="applicationId"
            label="Switch application"
            search-placeholder="Find application…"
            all-label="All applications"
            all-to="/applications"
            create-label="New application"
            create-to="/applications?add=true"
            @select="selectApp"
          />
        </div>
      </template>

      <!-- Inside an application with projects but none open: a way to pick one from here -->
      <template v-if="applicationId && !projectId && projectItems.length > 0">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <BreadcrumbSwitcher
          :items="projectItems"
          label="Select project"
          placeholder="Select project"
          search-placeholder="Find project…"
          all-label="All projects"
          :all-to="`${applicationPath}/projects`"
          create-label="New project"
          :create-to="`${applicationPath}/projects?openNewProject=1`"
          @select="selectProject"
        />
      </template>

      <template v-if="applicationId && projectId">
        <span class="text-lg text-surface-300 dark:text-surface-600">/</span>
        <div class="flex items-center gap-1">
          <RouterLink :to="`${applicationPath}/project/${encodeURIComponent(projectId)}`"
            class="flex items-center gap-2 text-sm font-medium text-surface-950 transition-opacity hover:opacity-70 dark:text-surface-100">
            {{ currentProjectName }}
            <ScopePill kind="project" />
          </RouterLink>
          <BreadcrumbSwitcher
            :items="projectItems"
            :current-id="projectId"
            label="Switch project"
            search-placeholder="Find project…"
            all-label="All projects"
            :all-to="`${applicationPath}/projects`"
            create-label="New project"
            :create-to="`${applicationPath}/projects?openNewProject=1`"
            @select="selectProject"
          />
        </div>
      </template>
    </div>

    <div class="flex items-center gap-2">
      <HeaderSearchButton @open="searchOpen = true" />

      <HeaderIconButton :href="DOCUMENTATION_URL" label="Help (opens the documentation in a new tab)" tooltip="Help">
        <CircleHelp :size="18" :stroke-width="1.75" aria-hidden="true" />
      </HeaderIconButton>
      <ThemeToggleButton />
    </div>

    <CommandPalette v-model:visible="searchOpen" :groups="searchGroups" label="Search applications, pages and actions"
                    @show="onSearchShow" />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { USER_STATE } from '@/states/IUserState';
import { Kinotic, Pageable } from '@kinotic-ai/core';
import type { Project } from '@kinotic-ai/management-api';
import { BreadcrumbSwitcher, CommandPalette, type CommandPaletteGroup, createDebug, HeaderIconButton, HeaderSearchButton, ScopePill,
         sidebarPageEntries, ThemeToggleButton } from '@kinotic-ai/frontend-common'
import { BookOpenText, CircleHelp, Plus } from '@lucide/vue';
import { DOCUMENTATION_URL } from '@/util/externalLinks';

const debug = createDebug('header');

const emit = defineEmits<{
  (e: 'toggle-nav'): void
}>()

/**
 * The breadcrumb across the top: organization / application / project, as deep as the
 * current route goes. The application and project segments are switchers; switching keeps
 * the page within the new scope where it exists there.
 */
const route = useRoute();
const router = useRouter();

const searchOpen = ref(false);

const projectsForCurrentApp = ref<Project[]>([]);

const searchGroups = computed<CommandPaletteGroup[]>(() => [
  {
    label: 'Applications',
    entries: APPLICATION_STATE.allApplications.map((app, position) => ({
      key: `app:${app.id}`,
      label: app.name || app.id,
      hint: app.id,
      tileIndex: position,
      run: () => router.push(`/application/${encodeURIComponent(app.id)}`)
    }))
  },
  {
    label: 'Pages',
    entries: sidebarPageEntries(router, { organization: 'Organization', account: 'Account' }, path => router.push(path))
  },
  {
    label: 'Actions',
    entries: [
      { key: 'action:new-application', label: 'New application', icon: markRaw(Plus), run: () => router.push('/applications?add=true') },
      { key: 'action:docs', label: 'Open documentation', icon: markRaw(BookOpenText), external: true,
        run: () => window.open(DOCUMENTATION_URL, '_blank', 'noopener') }
    ]
  }
]);

function onSearchShow() {
  if (APPLICATION_STATE.allApplications.length === 0) {
    void APPLICATION_STATE.loadAllApplications();
  }
}

const organizationId = computed(() => USER_STATE.getOrganizationId());
const applicationId = computed(() => route.params.applicationId as string | undefined);
const projectId = computed(() => route.params.projectId as string | undefined);

onMounted(() => {
  if (APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications();
  }
});

const applicationPath = computed(() => `/application/${encodeURIComponent(applicationId.value ?? '')}`);

const applicationItems = computed(() =>
    APPLICATION_STATE.allApplications.map(app => ({ id: app.id, label: app.name || app.id })));

const projectItems = computed(() =>
    projectsForCurrentApp.value.map(proj => ({ id: proj.id ?? '', label: proj.name })));

const currentProjectName = computed(() => {
  const project = projectsForCurrentApp.value.find(p => p.id === projectId.value);
  return project?.name ?? projectId.value ?? '';
});

watch(applicationId, onApplicationChanged, { immediate: true });
async function onApplicationChanged(id: string | undefined) {
  projectsForCurrentApp.value = [];
  if (id === undefined) {
    return;
  }
  if (APPLICATION_STATE.currentApplication?.id !== id) {
    await syncCurrentApplication(id);
  }
  await loadProjectsForCurrentApp(id);
}

/** Points the shared application state at the route's application. */
async function syncCurrentApplication(id: string): Promise<void> {
  try {
    if (APPLICATION_STATE.allApplications.length === 0) {
      await APPLICATION_STATE.loadAllApplications();
    }
    const listed = APPLICATION_STATE.allApplications.find(app => app.id === id);
    APPLICATION_STATE.currentApplication = listed ?? await Kinotic.applications.findById(id);
  } catch (error) {
    debug('Failed to load application %s: %O', id, error);
  }
}

async function loadProjectsForCurrentApp(id: string): Promise<void> {
  try {
    const result = await Kinotic.projects.findAllForApplication(id, Pageable.create(0, 100));
    // the route may have moved to another application while this request was in flight
    if (applicationId.value === id) {
      projectsForCurrentApp.value = result.content ?? [];
    }
  } catch (error) {
    debug('Failed to load projects for %s: %O', id, error);
  }
}

/** The part of the current path below the given scope path, or '' when not inside it. */
function pathBelow(scopePath: string): string {
  return route.path.startsWith(scopePath) ? route.path.slice(scopePath.length) : '';
}

function selectApp(id: string) {
  // only the section carries over: a project or an entity belongs to this application alone
  const section = projectId.value
      ? ''
      : pathBelow(`/application/${encodeURIComponent(applicationId.value ?? '')}`).split('/')[1];
  const below = section ? `/${section}` : '';
  router.push(`/application/${encodeURIComponent(id)}${below}`);
}

function selectProject(id: string) {
  const scopePath = `/application/${encodeURIComponent(applicationId.value ?? '')}/project/${encodeURIComponent(projectId.value ?? '')}`;
  // only the first segment carries over: an entity or job run belongs to this project alone
  const section = pathBelow(scopePath).split('/')[1];
  const below = section ? `/${section}` : '';
  router.push(`/application/${encodeURIComponent(applicationId.value ?? '')}/project/${encodeURIComponent(id)}${below}`);
}
</script>
